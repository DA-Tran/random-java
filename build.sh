#!/usr/bin/env bash
#
# Compiles the whole 128-project suite into build/classes.
#
#   ./build.sh            compile everything
#   JAVAC=... ./build.sh  use a specific compiler
#
# Prefers the JDK bundled in openJdk-25/ when it is executable here, which is
# the case in Git Bash on Windows. Falls back to whatever javac is on PATH.

set -euo pipefail
cd "$(dirname "$0")"

# A candidate only counts if it actually runs here. The executable bit is not
# enough: openJdk-25/ is a Windows build, and on a Linux mount of this repo the
# .exe is happily marked executable and then dies with "Exec format error".
usable() { [ -n "${1:-}" ] && "$1" -version >/dev/null 2>&1; }

if usable "${JAVAC:-}"; then
  :
else
  JAVAC=""
  # JAVA_HOME before bare PATH. Whoever set it meant it, and on a machine
  # with a JRE on PATH and a JDK in JAVA_HOME the other order compiles with one
  # and runs with the other, which fails later and much less clearly.
  for candidate in \
      "${JAVA_HOME:-}/bin/javac" \
      "./openJdk-25/bin/javac" \
      "./openJdk-25/bin/javac.exe" \
      "javac"; do
    if usable "$candidate"; then JAVAC="$candidate"; break; fi
  done
fi

if [ -z "$JAVAC" ]; then
  echo "No working Java compiler found." >&2
  echo "Install a JDK 17 or newer, or set JAVAC=/path/to/javac" >&2
  echo "A JDK bundled for another operating system will not do: this checked" >&2
  echo "that each candidate actually runs, not just that it exists." >&2
  exit 1
fi

OUT="${OUT:-build/classes}"
# Best effort clean. A locked file (an editor or a running JVM holding a class)
# should not abort the build, since javac overwrites what it produces anyway.
# The clean is opt-in: CLEAN=1 ./build.sh
#
# javac overwrites everything it produces, so a clean buys nothing except
# removing orphans - and the orphan check below reports those anyway. On a
# network or virtualised mount each unlink can cost tens of milliseconds, which
# across a couple of thousand class files turned a 30-second build into a
# 90-second one spent almost entirely in rm.
mkdir -p "$OUT" build
if [ "${CLEAN:-0}" = "1" ]; then
  echo "Cleaning $OUT (this is the slow part on a mounted filesystem)"
  rm -rf "$OUT" 2>/dev/null || echo "  (could not fully clean $OUT, continuing)"
  mkdir -p "$OUT"
fi
STARTED_AT="build/.build-started"
: > "$STARTED_AT"

echo "Compiling with $("$JAVAC" -version 2>&1)"

# One compilation unit for the lot: the generated Catalog references every
# project, so they all have to be on the same javac invocation anyway.
find lib projects -name "*.java" -print > build/sources.txt
echo "  $(wc -l < build/sources.txt) source files"

"$JAVAC" -encoding UTF-8 --release 17 -nowarn -d "$OUT" @build/sources.txt

# Every source is compiled in one javac call, so every class that is still
# live gets a fresh timestamp. Anything older than the marker is an orphan left
# by a class that has since been renamed or deleted - usually a scaffold inner
# record surviving a project graduating to hand-written code. Harmless until
# the day a stale class quietly satisfies a lookup that should have failed, so
# it is reported rather than ignored.
orphans=$(find "$OUT" -name "*.class" ! -newer "$STARTED_AT" 2>/dev/null | wc -l)
if [ "$orphans" -gt 0 ]; then
  echo "  warning: $orphans stale class file(s) survived the clean:"
  find "$OUT" -name "*.class" ! -newer "$STARTED_AT" 2>/dev/null | sed 's|^|    |' | head -10
  echo "    run CLEAN=1 ./build.sh to remove them"
fi

# Each project's ui.html has to sit beside its class on the classpath, because
# Project.uiFragment() loads it as a resource relative to the class.
copied=0
for ui in projects/*/ui.html; do
  [ -e "$ui" ] || continue
  dir="$(dirname "$ui")"
  java_file="$(find "$dir" -maxdepth 1 -name '*.java' | head -1)"
  [ -n "$java_file" ] || continue
  package="$(grep -m1 '^package ' "$java_file" | sed 's/^package //; s/;.*$//' | tr -d '[:space:]')"
  [ -n "$package" ] || continue
  dest="$OUT/$(echo "$package" | tr '.' '/')"
  mkdir -p "$dest"
  cp "$ui" "$dest/ui.html"
  copied=$((copied + 1))
done

echo "  $copied ui.html resources copied"
echo
echo "Built. Try:"
echo "  ./run.sh              interactive terminal menu"
echo "  ./run.sh web          browser hub on http://localhost:8080"
echo "  ./run.sh text 1       run project 1 in the terminal"
