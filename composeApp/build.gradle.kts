import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// ---------------------------------------------------------------- 构建标识
// 每次构建都会生成一个不同的戳（时间 + CI 上的 commit 短 SHA），
// 显示在界面右下角 —— 这样「你看的是不是最新版本」一眼可辨，不用靠猜。
val lumiVersion = "0.2.2"
val lumiStamp: String = run {
    // CI 上所有产物共用同一个 commit 短 SHA；本地构建用时间戳
    val sha = System.getenv("GITHUB_SHA")?.take(7).orEmpty()
    if (sha.isNotEmpty()) sha else LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMdd-HHmm"))
}

val generateBuildInfo = tasks.register("generateBuildInfo") {
    val outDir = layout.buildDirectory.dir("generated/lumicode")
    val version = lumiVersion
    val stamp = lumiStamp
    inputs.property("version", version)
    inputs.property("stamp", stamp)
    outputs.dir(outDir)
    doLast {
        val dir = outDir.get().asFile.resolve("com/lumicode/editor")
        dir.mkdirs()
        dir.resolve("BuildInfo.kt").writeText(
            """
            |package com.lumicode.editor
            |
            |/** 由 Gradle 在构建时生成；不要手改。 */
            |const val LUMICODE_VERSION = "$version"
            |const val LUMICODE_STAMP = "$stamp"
            |""".trimMargin(),
        )
    }
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        moduleName = "composeApp"
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
            }
        }
        binaries.executable()
    }

    // 生成的构建标识参与编译（srcDir 会自动带上任务依赖）
    sourceSets.named("commonMain") {
        kotlin.srcDir(generateBuildInfo)
    }

    sourceSets {
        val desktopMain by getting


        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
        }
        androidMain.dependencies {
            implementation(compose.uiTooling)
            implementation(libs.androidx.activity.compose)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

android {
    namespace = "com.lumicode.editor"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.lumicode.editor"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 4
        versionName = "0.2.2"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.lumicode.editor.resources"
    generateResClass = auto
}

compose.desktop {
    application {
        mainClass = "com.lumicode.editor.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            packageName = "LumiCode"
            packageVersion = "1.0.0"
            description = "LumiCode — Archive-style code editor"
            vendor = "LumiCode"
        }
    }
}
