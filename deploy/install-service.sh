#!/usr/bin/env bash
# 安装/更新用户级静态服务（无需 root，依赖 loginctl enable-linger，本机已是 Linger=yes）
set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UNIT_DIR="$HOME/.config/systemd/user"
mkdir -p "$UNIT_DIR"
cp "$REPO/deploy/systemd/lumicode-web.service" "$UNIT_DIR/lumicode-web.service"
systemctl --user daemon-reload
systemctl --user enable --now lumicode-web.service
systemctl --user status lumicode-web.service --no-pager | head -6
