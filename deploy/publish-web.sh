#!/usr/bin/env bash
# 把构建好的 Wasm 产物发布到 ~/.local/share/lumicode/web 并重启静态服务。
# 之后 http://<host>:11024/code/ 就是最新版本（Caddy 侧无需改动）。
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$REPO/composeApp/build/dist/wasmJs/productionExecutable"
DEST="${LUMICODE_WEB_ROOT:-$HOME/.local/share/lumicode/web}"
UNIT=lumicode-web.service

if [[ ! -f "$SRC/index.html" ]]; then
  echo "找不到构建产物: $SRC" >&2
  echo "先执行: ./gradlew :composeApp:wasmJsBrowserDistribution" >&2
  exit 1
fi

echo "==> 同步 $SRC -> $DEST"
mkdir -p "$DEST"
if command -v rsync >/dev/null 2>&1; then
  rsync -a --delete "$SRC/" "$DEST/"
else
  rm -rf "${DEST:?}"/* && cp -r "$SRC"/* "$DEST"/
fi

if systemctl --user list-unit-files "$UNIT" >/dev/null 2>&1; then
  echo "==> 重启 $UNIT"
  systemctl --user restart "$UNIT"
  systemctl --user is-active "$UNIT"
else
  echo "!! 未安装用户服务 $UNIT，请先执行 deploy/install-service.sh"
fi

echo "==> 完成：通过 Caddy 访问 http://127.0.0.1:11024/code/"
