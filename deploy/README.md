# 把 LUMICODE 的 Web(Wasm) 版挂到 `11024/code`

本机的 Caddy（`services.caddy`，监听 `:11024`）已经代理了 `/ds`、`/rtwind`、`/wind`、`/love`
等路径。这里用**和 `/ds` 相同的原理**再加一个 `/code`：Caddy 只负责路径前缀与反代，
真正的静态文件由一个**用户级 systemd 服务**提供（这样不需要 root，也不用让 Caddy 的用户
去读 `$HOME` 下的构建产物）。

```
浏览器 ──▶ Caddy :11024 ──▶ /code/*  剥离前缀 ──▶ reverse_proxy 127.0.0.1:8099
                                                   └─ lumicode-web.service
                                                      (tools/serve_wasm.py, 用户级)
                                                      文件来自 ~/.local/share/lumicode/web
```

## 一次性安装

```bash
# 1) 用户级静态服务（无需 root；本机 loginctl 已是 Linger=yes，可常驻）
./deploy/install-service.sh

# 2) 让 Caddy 知道 /code（持久化，需要 root 改 /etc/nixos/configuration.nix）
sudo python3 deploy/nixos/patch-caddy.py --dry-run   # 先看要插入什么
sudo python3 deploy/nixos/patch-caddy.py             # 会自动备份 configuration.nix
sudo nixos-rebuild switch
```

> 首次部署时我没有 root，所以是先通过 Caddy 的 **admin API**（`localhost:2019`）把同样的
> 规则热加载进去、立即生效；那份配置**不持久**，`nixos-rebuild` 或重启 Caddy 后需要上面
> 第 2 步写入的 `configuration.nix` 来接管。

## 日常更新前端

```bash
./gradlew :composeApp:wasmJsBrowserDistribution   # 重新构建
./deploy/publish-web.sh                           # 同步产物 + 重启用户服务
```

Caddy 侧不需要任何改动，刷新 `http://<host>:11024/code/` 即可。

## 常用排查

```bash
systemctl --user status lumicode-web.service      # 服务状态
journalctl --user -u lumicode-web.service -f      # 访问日志
curl -I http://127.0.0.1:11024/code/              # 经过 Caddy 的结果
curl -I http://127.0.0.1:8099/                    # 绕过 Caddy 直连静态服务
```

## 已知细节

- `/code`（不带斜杠）会 **308 跳到 `/code/`**：页面里的资源是相对路径，缺少结尾斜杠时
  浏览器会按 `/` 解析 `composeApp.js` 而 404。
- 静态服务显式声明 `application/wasm`、`font/ttf`、`font/otf`：Kotlin/Wasm 的加载器遇到
  错误的 MIME 会退化（甚至报错），`python3 -m http.server` 在这点上不可靠。
- `/code` **没有**加 `basic_auth`（和 `/love` 一致）；若要像 `/ds` 那样加一层认证，
  把 `deploy/nixos/patch-caddy.py` 里的片段和运行时补丁都改成
  `handle_path /code/* { basic_auth { ... } reverse_proxy ... }` 即可。
