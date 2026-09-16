#!/usr/bin/env bash
#
# Runs the suite, building first if it has not been built yet.
#
#   ./run.sh                 interactive terminal menu
#   ./run.sh text 4          run project 4 in the terminal
#   ./run.sh text bmi        ...by name
#   ./run.sh web             browser hub on http://localhost:8080
#   ./run.sh web 9000        ...on another port
#   ./run.sh list maze       search the catalogue

set -euo pipefail
cd "$(dirname "$0")"

if [ ! -d build/classes ] || [ -z "$(ls -A build/classes 2>/dev/null)" ]; then
  ./build.sh
fi

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

# JAVA_HOME before bare PATH: the classes are built for 17, and a JRE 11 on
# PATH would be picked over a perfectly good JDK the user already pointed at.
usable "${JAVA:-}" || JAVA="$(pick "${JAVA_HOME:-}/bin/java" ./openJdk-25/bin/java ./openJdk-25/bin/java.exe java)"
if [ -z "$JAVA" ]; then
  echo "No working Java runtime found. Set JAVA=/path/to/java." >&2
  exit 1
fi

exec "$JAVA" -cp build/classes com.randomjava.Launcher "$@"
