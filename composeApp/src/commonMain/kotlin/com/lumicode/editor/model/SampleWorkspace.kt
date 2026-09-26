package com.lumicode.editor.model

/**
 * A small in-memory workspace. Everything the demo edits lives here, which keeps
 * behaviour identical on Android, desktop (Windows/Linux) and WebAssembly.
 */
object SampleWorkspace {

    private const val D = "${'$'}"

    private fun meta(
        no: String,
        dept: String,
        deptCn: String,
        collection: String,
        collectionCn: String,
        related: String,
        abstract: String,
    ) = FileMeta(
        archiveNo = no,
        department = dept,
        departmentCn = deptCn,
        collection = collection,
        collectionCn = collectionCn,
        related = related,
        status = "ARCHIVED · READABLE",
        statusCn = "已归档 · 可读取",
        abstract = abstract,
    )

    val files: List<CodeFile> = listOf(
        CodeFile(
            path = "src/Main.kt",
            meta = meta(
                no = "X-001",
                dept = "MAIN CONTROL",
                deptCn = "主控构件科",
                collection = "ENTRY POINT",
                collectionCn = "入口汇编",
                related = "Joyce Moore / Saria",
                abstract = "工作区入口。负责装配窗口、主题与全局快捷键，并把编辑器的状态树挂载到 Compose 运行时上。" +
                    "所有平台共用同一份实现，只有入口函数按平台分别声明。",
            ),
            content = """
                package lumicode.app

                import androidx.compose.runtime.LaunchedEffect
                import com.lumicode.editor.App

                /**
                 * LUMICODE ANALYSIS OS — 工作区入口。
                 * 档案 NO.001 / 内部数据库。
                 * 负责装配窗口、主题与全局快捷键。
                 */
                fun main() {
                    val state = rememberIdeState(files = SampleWorkspace.files)
                    App(state)
                }

                // 启动序列：挂载 → 校验 → 授权
                private fun bootSequence(): List<String> = listOf(
                    "mount /dev/archive",
                    "verify signature",
                    "authorize session",
                )

                fun boot(log: (String) -> Unit) {
                    bootSequence().forEachIndexed { index, step ->
                        log("[${D}{index.toString().padStart(2, '0')}] ${D}step")
                    }
                }
            """.trimIndent(),
        ),
        CodeFile(
            path = "src/core/Archive.kt",
            meta = meta(
                no = "X-002",
                dept = "CORE SYSTEMS",
                deptCn = "核心系统科",
                collection = "RUNTIME KERNEL",
                collectionCn = "运行时内核",
                related = "Kristen Wright / Saria",
                abstract = "核心归档模型。定义文件条目、索引协议与访问令牌；所有写入都经过一次签名校验，" +
                    "因此编辑动作本身也是一条可追溯的记录。",
            ),
            content = """
                package lumicode.core

                /** 单个档案条目的访问级别。 */
                enum class Clearance(val level: Int, val label: String) {
                    PUBLIC(0, "public"),
                    LAB(1, "laboratory"),
                    SEALED(9, "sealed"),
                }

                data class ArchiveEntry(
                    val no: String,
                    val title: String,
                    val clearance: Clearance = Clearance.LAB,
                    val tags: List<String> = emptyList(),
                ) {
                    val display: String get() = "#${D}no · ${D}{title.uppercase()}"

                    fun readableBy(token: Token): Boolean =
                        token.level >= clearance.level
                }

                class Token(private val level: Int) {
                    val level: Int get() = level
                    companion object {
                        val GUEST = Token(0)
                        val OPERATOR = Token(1)
                    }
                }

                // TODO: 把签名校验拆到独立模块
                fun verify(entry: ArchiveEntry, token: Token): Boolean {
                    if (!entry.readableBy(token)) return false
                    return entry.no.startsWith("X-")
                }

                fun indexOf(entries: List<ArchiveEntry>): Map<String, ArchiveEntry> =
                    entries.associateBy { it.no }
            """.trimIndent(),
        ),
        CodeFile(
            path = "src/ui/Viewport.kt",
            meta = meta(
                no = "X-003",
                dept = "VISUAL ANALYSIS",
                deptCn = "视觉分析科",
                collection = "DRAWING SURFACE",
                collectionCn = "绘制界面",
                related = "Ifrit / Ptilopsis",
                abstract = "绘制层与版式栅格。整个界面是一块连续的冷调平面：没有卡片、没有圆角、没有描边，" +
                    "区域之间只靠留白与字重区分，颜色只留给“活的”信号。",
            ),
            content = """
                package lumicode.ui

                import androidx.compose.foundation.background
                import androidx.compose.foundation.layout.Box
                import androidx.compose.foundation.layout.fillMaxSize
                import androidx.compose.runtime.Composable
                import androidx.compose.ui.Modifier

                /** 唯一的场：一整块连续底色，一直铺到窗口边缘。 */
                @Composable
                fun Viewport(
                    grid: Grid = Grid.Archive,
                    content: @Composable () -> Unit,
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(grid.field),
                    ) {
                        content()
                    }
                }

                enum class Grid(val columns: Int, val gutter: Int) {
                    Archive(12, 24),
                    Compact(6, 16),
                }

                /* 无界：区域之间不画线，层次只用极淡的一层色暗示 */
                fun Modifier.layer(tone: Tone): Modifier = this.background(tone.wash)
            """.trimIndent(),
        ),
        CodeFile(
            path = "docs/README.md",
            meta = meta(
                no = "D-001",
                dept = "DOCUMENTATION",
                deptCn = "文档资料科",
                collection = "OPERATING MANUAL",
                collectionCn = "操作手册",
                related = "Joyce Moore",
                abstract = "操作手册。说明快捷键、档案编号规则，以及为什么这个编辑器把所有面板都叫做“档案区”。",
            ),
            content = """
                # LUMICODE — ANALYSIS OS

                > 无界风格代码编辑器：一整块连续平面，只靠留白与字重分区。

                ## Shortcuts

                | Key | Action |
                | --- | --- |
                | Ctrl/Cmd + K | Command index |
                | Ctrl/Cmd + P | Quick open |
                | Ctrl/Cmd + S | Save archive |
                | Ctrl/Cmd + F | Find in file |
                | Ctrl/Cmd + B | Toggle explorer |
                | Ctrl/Cmd + J | Toggle output |
                | F5 | Run / analyse |

                ## Conventions

                1. Every file carries an archive number `NO.xxx`.
                2. No panels, no cards, no rounded corners, no borders — one continuous field.
                3. Whitespace and type weight do the dividing.
                4. Chroma is reserved for live signals only.

                <!-- TODO: 补充 Wasm 构建流程说明 -->
            """.trimIndent(),
        ),
        CodeFile(
            path = "assets/index.json",
            meta = meta(
                no = "J-001",
                dept = "DATA WAREHOUSE",
                deptCn = "数据仓库科",
                collection = "ASSET INDEX",
                collectionCn = "资产索引",
                related = "Silence",
                abstract = "资产索引清单。记录每个可视化节点的引用路径与刷新频率。",
            ),
            content = """
                {
                  "archive": "RHINE-OS",
                  "revision": 16,
                  "nodes": [
                    { "id": "viewport", "path": "src/ui/Viewport.kt", "fps": 60 },
                    { "id": "archive", "path": "src/core/Archive.kt", "fps": 30 },
                    { "id": "index", "path": "assets/index.json", "fps": 12 }
                  ],
                  "telemetry": { "udp": true, "fps": 21, "throughput": "2.1 TiB" }
                }
            """.trimIndent(),
        ),
        CodeFile(
            path = "build.gradle.kts",
            meta = meta(
                no = "B-001",
                dept = "BUILD ENGINEERING",
                deptCn = "构建工程科",
                collection = "TOOLCHAIN",
                collectionCn = "工具链",
                related = "Saria",
                abstract = "构建脚本。四个目标平台共用一份 commonMain 源码：Android、JVM 桌面、WebAssembly，以及测试。",
            ),
            content = """
                plugins {
                    alias(libs.plugins.kotlinMultiplatform)
                    alias(libs.plugins.composeMultiplatform)
                }

                kotlin {
                    androidTarget()
                    jvm("desktop")
                    wasmJs { browser() }

                    sourceSets {
                        commonMain.dependencies {
                            implementation(compose.runtime)
                            implementation(compose.foundation)
                            implementation(compose.ui)
                        }
                    }
                }
            """.trimIndent(),
        ),
    )
}
