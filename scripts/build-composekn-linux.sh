#!/usr/bin/env bash
# Build LumiCode linuxX64 via sibling ComposeKN sample.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSEKN="${COMPOSEKN_ROOT:-$(cd "$ROOT/../ComposeKN" && pwd)}"

if [[ ! -f "$COMPOSEKN/settings.gradle.kts" ]]; then
  echo "ComposeKN not found at $COMPOSEKN" >&2
  echo "Clone https://github.com/TetraploidHuman/ComposeKN next to LumiCodeNext" >&2
  exit 1
fi

export https_proxy="${https_proxy:-http://172.20.128.142:7897}"
export http_proxy="${http_proxy:-http://172.20.128.142:7897}"
export HTTPS_PROXY="${HTTPS_PROXY:-$https_proxy}"
export HTTP_PROXY="${HTTP_PROXY:-$http_proxy}"

cd "$COMPOSEKN"
if command -v nix-shell >/dev/null 2>&1 && [[ -f shell.nix ]]; then
  nix-shell ./shell.nix --run "./gradlew :samples:lumicode:linkReleaseExecutableLinuxX64 --no-daemon $*"
else
  ./gradlew :samples:lumicode:linkReleaseExecutableLinuxX64 --no-daemon "$@"
fi

echo
echo "Binary: $COMPOSEKN/samples/lumicode/build/bin/linuxX64/releaseExecutable/lumicode.kexe"
echo "Run:    $COMPOSEKN/scripts/run-linux-native.sh <that-kexe>"
