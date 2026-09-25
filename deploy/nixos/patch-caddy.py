#!/usr/bin/env python3
"""把 /code 反代规则并入 /etc/nixos/configuration.nix 的 Caddy 配置（幂等）。

运行时我也用 Caddy 的 admin API 热加载了同样一份规则（免 root、立即生效），
但那份不持久：`nixos-rebuild switch` 或重启 Caddy 会按 configuration.nix 重新生成，
所以持久化需要这个脚本 + 一次 rebuild。

用法（需要 root，因为要写 /etc/nixos）：

    sudo python3 deploy/nixos/patch-caddy.py --dry-run     # 先看会改什么
    sudo python3 deploy/nixos/patch-caddy.py              # 备份后写入
    sudo nixos-rebuild switch                             # 生效

设计要点：
* 只往每个 vhost 的 extraConfig 里插，且必须插在末尾的 catch-all `handle {` **之前**，
  否则 Caddy 会先命中 "Hello from NixOS" 而永远走不到 /code。
* 幂等：已经存在 /code 规则的 vhost 会跳过。
* 会先备份成 configuration.nix.bak.<时间戳>。
"""

from __future__ import annotations

import argparse
import datetime
import os
import re
import shutil
import sys

CONFIG = "/etc/nixos/configuration.nix"
VHOST_RE = re.compile(r'virtualHosts\."http://(?P<host>[^"]+):11024"\.extraConfig = \'\'')

SNIPPET = """
      # ==== LUMICODE web（Kotlin/Wasm）====
      # 静态文件由用户级 systemd 服务 lumicode-web.service 提供（127.0.0.1:8099），
      # 这里只做反代；重建前端后跑 deploy/publish-web.sh 即可刷新。
      @lumicodeCode path /code
      redir @lumicodeCode /code/ 308
      handle_path /code/* {
        reverse_proxy 127.0.0.1:8099 {
          header_up Host {host}
        }
      }
"""


def patch(text: str) -> tuple[str, list[str]]:
    lines = text.split("\n")
    out: list[str] = []
    patched_hosts: list[str] = []
    index = 0

    while index < len(lines):
        line = lines[index]
        match = VHOST_RE.search(line)
        if not match:
            out.append(line)
            index += 1
            continue

        host = match.group("host")
        out.append(line)
        index += 1

        # 收集这个 vhost 的块
        block: list[str] = []
        while index < len(lines) and lines[index].strip() != "'';":
            block.append(lines[index])
            index += 1

        if "/code" in "\n".join(block):
            print(f"  [{host}] 已存在 /code 规则，跳过")
        else:
            insert_at = len(block)
            for position in range(len(block) - 1, -1, -1):
                if block[position].strip() == "handle {":
                    insert_at = position
                    break
            block[insert_at:insert_at] = SNIPPET.split("\n")[1:-1]
            patched_hosts.append(host)

        out.extend(block)
        if index < len(lines):
            out.append(lines[index])
            index += 1

    return "\n".join(out), patched_hosts


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dry-run", action="store_true", help="只打印将要插入的内容")
    parser.add_argument("--file", default=CONFIG, help="configuration.nix 路径")
    args = parser.parse_args()

    if not os.path.exists(args.file):
        print(f"找不到 {args.file}", file=sys.stderr)
        return 1

    original = open(args.file, encoding="utf-8").read()
    updated, hosts = patch(original)

    if not hosts:
        print("没有需要修改的 vhost（可能已经打过补丁）")
        return 0

    print(f"将给这些 vhost 插入 /code 规则: {', '.join(hosts)}")
    if args.dry_run:
        print("---- 插入内容 ----")
        print(SNIPPET)
        return 0

    stamp = datetime.datetime.now().strftime("%Y%m%d%H%M%S")
    backup = f"{args.file}.bak.{stamp}"
    shutil.copy2(args.file, backup)
    with open(args.file, "w", encoding="utf-8") as handle:
        handle.write(updated)
    print(f"已写入 {args.file}（备份: {backup}）")
    print("接下来执行: sudo nixos-rebuild switch")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
