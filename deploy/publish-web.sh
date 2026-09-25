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

# 非登录 shell 里没有会话总线变量，这里补上（脚本常被 CI/定时任务调用）
export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}"
if [[ -z "${DBUS_SESSION_BUS_ADDRESS:-}" && -S "$XDG_RUNTIME_DIR/bus" ]]; then
  export DBUS_SESSION_BUS_ADDRESS="unix:path=$XDG_RUNTIME_DIR/bus"
fi

if systemctl --user restart "$UNIT" 2>/dev/null; then
  echo "==> 已重启 $UNIT：$(systemctl --user is-active "$UNIT")"
else
  # 静态服务每次请求都从磁盘读，未重启也是最新内容；只是让状态更干净
  echo "!! 无法通过 systemctl --user 重启（可能未安装该单元），"
  echo "   静态文件已同步，服务下次启动即用新内容；如需立即重启请执行 deploy/install-service.sh"
fi

echo "==> 完成：通过 Caddy 访问 http://127.0.0.1:11024/code/"
