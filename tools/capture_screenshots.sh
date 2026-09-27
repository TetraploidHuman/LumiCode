#!/usr/bin/env bash
# Capture README screenshots from the published Wasm UI under Xvfb + Firefox.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${1:-$ROOT/docs/screenshots}"
URL="${LUMICODE_URL:-http://127.0.0.1:11024/code/}"
DISPLAY_NUM=97
export DISPLAY=":${DISPLAY_NUM}"

need() { command -v "$1" >/dev/null || { echo "missing $1"; exit 1; }; }
need Xvfb; need firefox; need xdotool; need import

mkdir -p "$OUT" /tmp/lumi-ff-profile
rm -rf /tmp/lumi-ff-profile/*
pkill -f "Xvfb :${DISPLAY_NUM}" 2>/dev/null || true
sleep 0.3

Xvfb ":${DISPLAY_NUM}" -screen 0 1600x940x24 -ac >/tmp/lumi-xvfb.log 2>&1 &
XVFB_PID=$!
trap 'kill $FF_PID 2>/dev/null; kill $XVFB_PID 2>/dev/null; true' EXIT
sleep 0.5

firefox --new-instance --no-remote \
  --profile /tmp/lumi-ff-profile \
  --width 1600 --height 940 \
  "$URL" >/tmp/lumi-ff.log 2>&1 &
FF_PID=$!

echo "==> waiting for Wasm canvas…"
# Poll until the splash is gone (screenshot no longer tiny / contains canvas paint)
for i in $(seq 1 60); do
  sleep 2
  import -window root /tmp/lumi-probe.png 2>/dev/null || continue
  SIZE=$(stat -c%s /tmp/lumi-probe.png 2>/dev/null || echo 0)
  # Loaded UI screenshots are typically >80KB
  if [ "$SIZE" -gt 90000 ]; then
    echo "    ready after ${i}x2s (${SIZE} bytes)"
    break
  fi
  echo "    … still loading (${SIZE} bytes)"
done

WID=$(xdotool search --onlyvisible --class firefox | head -1 || true)
if [ -z "${WID:-}" ]; then
  WID=$(xdotool search --onlyvisible --name LUMICODE | head -1 || true)
fi
if [ -z "${WID:-}" ]; then
  WID=$(xdotool search --onlyvisible --class Navigator | head -1 || true)
fi
if [ -z "${WID:-}" ]; then
  echo "firefox window not found"; xdotool search --name . || true; exit 1
fi
# bare Xvfb has no WM — skip windowactivate, address keys by window id
echo "    window=$WID"

shot() {
  local name="$1"
  import -window root "$OUT/$name"
  echo "    saved $name ($(stat -c%s "$OUT/$name") bytes)"
}

key() { xdotool key --window "$WID" "$@"; }
type_text() { xdotool type --window "$WID" --delay 25 "$@"; }
click_at() { xdotool mousemove --window "$WID" "$1" "$2" click 1; }

# Focus the page body for shortcuts (canvas area under browser chrome)
click_at 800 470
sleep 0.5

echo "==> 01 desktop overview"
shot 01-desktop-overview.png
cp -f "$OUT/01-desktop-overview.png" "$OUT/08-web-wasm.png"

echo "==> 02 editing (click into editor)"
click_at 700 420
sleep 0.3
type_text '//'
sleep 0.3
shot 02-editing.png
key ctrl+z
sleep 0.2

echo "==> 03 command index"
key ctrl+k
sleep 0.7
shot 03-command-index.png
key Escape
sleep 0.5

echo "==> 04 find in document"
key ctrl+f
sleep 0.5
type_text 'fun'
sleep 0.4
shot 04-find-in-document.png
key Escape
sleep 0.4

echo "==> 05 analysis pass"
key F5
sleep 2.5
shot 05-analysis-pass.png

echo "==> 06 settings"
key ctrl+k
sleep 0.5
type_text '设置'
sleep 0.3
key Return
sleep 0.8
shot 06-settings.png
key Escape
sleep 0.5

echo "==> 07 workspace overview"
key Escape
sleep 0.8
shot 07-workspace-overview.png
key Escape
sleep 0.5

echo "==> 11 markdown preview"
key ctrl+p
sleep 0.5
type_text 'README.md'
sleep 0.3
key Return
sleep 1.0
shot 11-markdown-preview.png

echo "==> 12 replace / find options"
key ctrl+h
sleep 0.6
shot 12-find-replace.png
key Escape
sleep 0.3

echo "==> 09 compact phone"
convert "$OUT/01-desktop-overview.png" -resize 430x920^ -gravity center -extent 430x920 "$OUT/09-compact-phone.png"
echo "    saved 09-compact-phone.png (resized stand-in)"

echo "==> 10 line highlight detail (crop from editing)"
convert "$OUT/02-editing.png" -crop 520x280+480+300 +repage "$OUT/10-line-highlight-detail.png"
echo "    saved 10-line-highlight-detail.png"

echo "==> done → $OUT"
ls -la "$OUT"
