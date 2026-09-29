#!/usr/bin/env bash
# 安装/更新用户级静态服务（无需 root，依赖 loginctl enable-linger，本机已是 Linger=yes）
# 含：Wasm 静态站 + DSH 桥（连本机已运行的 dsh-web，不重启 dsh）
set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UNIT_DIR="$HOME/.config/systemd/user"
mkdir -p "$UNIT_DIR"
cp "$REPO/deploy/systemd/lumicode-web.service" "$UNIT_DIR/lumicode-web.service"
cp "$REPO/deploy/systemd/lumicode-dsh-bridge.service" "$UNIT_DIR/lumicode-dsh-bridge.service"
systemctl --user daemon-reload
systemctl --user enable --now lumicode-dsh-bridge.service
systemctl --user enable --now lumicode-web.service
systemctl --user status lumicode-dsh-bridge.service --no-pager | head -6
systemctl --user status lumicode-web.service --no-pager | head -6
