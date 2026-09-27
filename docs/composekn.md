# ComposeKN / Kotlin Native 实验

分支：`experiment/composekn-native`

用旁边的 [ComposeKN](https://github.com/TetraploidHuman/ComposeKN)（Kotlin/Native + Wayland / Win32）
构建 LumiCode 的 **Linux** 与 **Windows** 桌面端，替代现有 JVM Compose Desktop + jpackage。

## 布局

```
~/ComposeKN/                     # sibling clone（ComposeKN 分支 experiment/lumicode-sample）
  samples/lumicode/              # 本实验 sample：源码指向 LumiCodeNext commonMain
  compose-kn-resources/          # Native 侧 Res.font.*（官方无 linuxX64/mingwX64 变体）
~/LumiCodeNext/                  # 本仓库
  composeApp/src/commonMain/     # UI 共享
  composeApp/src/commonMain/composeResources/font/  # Noto + JB Mono（两端共用）
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
  './gradlew :samples:lumicode:linkReleaseExecutableLinuxX64Stable --no-daemon'

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
（旁路应有 `composeResources/font/`，与 exe 一起发布）

## 状态

- ✅ Linux / Windows Native 可链
- ✅ **自定义字体**（ComposeKN v0.5.53）：`compose-kn-resources` + Gradle 插件生成 `Res.font.*`，
  与 Desktop 同一批 Noto / JetBrains Mono；`InstallArchiveFonts` KN actual 已接
- 运行：Linux 需 Wayland；Windows 可拷实机或 Wine

## 与 JVM 桌面的差异（当前）

| | JVM Desktop（main） | ComposeKN（本分支实验） |
|---|---|---|
| 运行时 | 捆绑 JRE | 单一 `.kexe` / `.exe` |
| 窗口 | AWT | Wayland / Win32 |
| 字体 | 官方 `compose.resources` → `Font(Res.font…)` | `com.composekn.resources.Font(Res.font…)`（同文件） |
| 平台标签 | `· JVM` | `· KN` |

## LumiCode 侧改动

- `InstallArchiveFonts` 为 expect/actual：JVM/Wasm/Android 用官方 API，KN 用 compose-kn-resources，字体文件共用
