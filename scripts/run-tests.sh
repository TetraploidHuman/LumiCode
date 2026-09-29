#!/usr/bin/env bash
# Run LumiCode automated tests (workspace snapshot + Kotlin parsers).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "==> Python: workspace snapshot / HTTP"
python3 -m unittest discover -s tools/tests -p 'test_*.py' -v

echo "==> Kotlin: desktopTest (includes commonTest)"
./gradlew :composeApp:desktopTest --quiet

echo "==> All tests passed"
