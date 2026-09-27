# ComposeKN / Kotlin Native 实验

分支：`experiment/composekn-native`

用旁边的 [ComposeKN](https://github.com/TetraploidHuman/ComposeKN)（Kotlin/Native + Wayland / Win32）
构建 LumiCode 的 **Linux** 与 **Windows** 桌面端，替代现有 JVM Compose Desktop + jpackage。

## 布局

```
~/ComposeKN/                     # sibling clone（ComposeKN 分支 experiment/lumicode-sample）
  samples/lumicode/              # 本实验 sample：源码指向 LumiCodeNext commonMain
~/LumiCodeNext/                  # 本仓库
  composeApp/src/commonMain/     # UI 共享
  docs/composekn.md              # 本文
```

## 前置

- ComposeKN 已能链接并跑 `wayland-demo`（见 ComposeKN `RUNNING.md` / `shell.nix`）
- 本机代理（若需要）：`http://172.20.128.142:7897`
- NixOS：在 `nix-shell ./shell.nix` 里构建

## Linux（本机）

```bash
cd ~/ComposeKN
nix-shell ./shell.nix --run \
  './gradlew :samples:lumicode:linkReleaseExecutableLinuxX64 --no-daemon'

# 运行（Wayland）
./scripts/run-linux-native.sh \
  samples/lumicode/build/bin/linuxX64/releaseExecutable/lumicode.kexe
```

## Windows（mingwX64）

在 Linux 上交叉链接（ComposeKN CI 同款预编译 MinGW Skia）：

```bash
cd ~/ComposeKN
./scripts/fetch-skia-mingw.sh   # 需 gh auth（私有 Release）
SKIA_MINGW_PREBUILT=/tmp/composekn-skia-mingw/skia-mingw-$(cat vendor/skiko/skia-mingw/SKIA_TAG) \
  nix-shell ./shell.nix --run './samples/lumicode/build-windows.sh'
```

产物：`samples/lumicode/build/bin/mingwX64/releaseExecutable/lumicode.exe`

## 状态（本机已验证）

- ✅ Linux：`lumicode.kexe` ~41MB（`linkReleaseExecutableLinuxX64Stable`）
- ✅ Windows：`lumicode.exe` ~39MB（`samples/lumicode/build-windows.sh` + MinGW Skia 预编译包）
- 运行：Linux 需 Wayland 会话 + `./scripts/run-linux-native.sh`；Windows 可拷到实机或 Wine 试跑

## 与 JVM 桌面的差异（当前）

| | JVM Desktop（main） | ComposeKN（本分支实验） |
|---|---|---|
| 运行时 | 捆绑 JRE | 单一 `.kexe` / `.exe` |
| 窗口 | AWT | Wayland / Win32 |
| 字体 | 内置 Noto + JB Mono | 系统字体回退 |
| 平台标签 | `· JVM` | `· KN` |

## LumiCode 侧改动

- `InstallArchiveFonts` 改为 expect/actual，便于 Native 不依赖 compose.resources
