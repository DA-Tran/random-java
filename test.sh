#!/usr/bin/env bash
#
# Compiles and runs the whole test suite.
#
#   ./test.sh
#
# Tests live in tests/ and compile into build/test-classes, which keeps them
# out of the shipped build in build/classes. Exits non-zero on any failure.

set -euo pipefail
cd "$(dirname "$0")"

# A candidate only counts if it actually runs here. The executable bit is not
# enough: openJdk-25/ is a Windows build, and on a Linux mount of this repo the
# .exe is happily marked executable and then dies with "Exec format error".
usable() { [ -n "${1:-}" ] && "$1" -version >/dev/null 2>&1; }

pick() {  # pick VAR_VALUE candidate...
  local found=""
  for candidate in "$@"; do
    if usable "$candidate"; then found="$candidate"; break; fi
  done
  printf '%s' "$found"
}

# JAVA_HOME before bare PATH, and the same order for both, so the compiler and
# the runtime come from one JDK. Compiling with 21 and running with a JRE 11 on
# PATH gives an UnsupportedClassVersionError with no hint at the real cause.
usable "${JAVAC:-}" || JAVAC="$(pick "${JAVA_HOME:-}/bin/javac" ./openJdk-25/bin/javac ./openJdk-25/bin/javac.exe javac)"
usable "${JAVA:-}"  || JAVA="$(pick "${JAVA_HOME:-}/bin/java"  ./openJdk-25/bin/java  ./openJdk-25/bin/java.exe  java)"
if [ -z "$JAVAC" ] || [ -z "$JAVA" ]; then
  echo "No working JDK found. Set JAVAC and JAVA, or install a JDK 17 or newer." >&2
  exit 1
fi

# The suite is built with --release 17, so a runtime older than that cannot
# load it. Say so here rather than letting the class loader say it.
JAVA_MAJOR=$("$JAVA" -version 2>&1 | head -1 | sed -E 's/[^0-9]*([0-9]+).*/\1/')
if [ -n "$JAVA_MAJOR" ] && [ "$JAVA_MAJOR" -lt 17 ] 2>/dev/null; then
  echo "Java $JAVA_MAJOR cannot run classes built for 17." >&2
  echo "  javac: $JAVAC" >&2
  echo "  java:  $JAVA" >&2
  echo "Point JAVA at the same JDK as JAVAC, or set JAVA_HOME." >&2
  exit 1
fi

if [ ! -d build/classes ] || [ -z "$(ls -A build/classes 2>/dev/null)" ]; then
  ./build.sh
fi

OUT="build/test-classes"
rm -rf "$OUT" 2>/dev/null || true
mkdir -p "$OUT"

find tests -name "*.java" -print > build/test-sources.txt
"$JAVAC" -encoding UTF-8 --release 17 -nowarn -cp build/classes -d "$OUT" @build/test-sources.txt

exec "$JAVA" -Xss8m -cp "build/classes:$OUT" com.randomjava.test.AllTests
