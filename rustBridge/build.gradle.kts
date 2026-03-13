import org.gradle.internal.os.OperatingSystem
import java.util.Properties

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library") // AGP 9.0 KMP 专用架构
}

/* ==========================================================================
 * 1. 路径与环境变量初始化
 * ========================================================================== */
val rustProjectDir: File = rootProject.file("rust-core")
val rustTargetDir: File = rustProjectDir.resolve("target")

// 【混合路由策略】
val buildDir = layout.buildDirectory.get().asFile
// 1. Kotlin 接口代码和 JVM 本地资源放入 build 目录（保持 src 纯净）
val generatedKotlinDir = File(buildDir, "generated/rust/uniffi/kotlin")
val generatedJvmResourcesDir = File(buildDir, "generated/rust/resources")

// 2. Android 的 JNI 库放回 src 目录，顺应 AGP 9.0 默认嗅探机制
// ⚠️ 极其重要：必须在 .gitignore 中忽略此目录！(src/androidMain/jniLibs/)
val jniLibsDir = projectDir.resolve("src/androidMain/jniLibs")

val localPropertiesFile: File = rootProject.file("local.properties")
val localProperties = Properties()
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { stream ->
        localProperties.load(stream)
    }
}

val cargoPath: String = localProperties.getProperty("cargo.path")
    ?: error("cargo.path not set (use local.properties or -Pcargo.path=...)")
val ndkHome: String = localProperties.getProperty("ndk.dir")
    ?: error("ndk.dir not set (use local.properties or -Pndk.dir=...)")

/* ==========================================================================
 * 2. KMP 与 Android 基础配置
 * ========================================================================== */
kotlin {
    // Android 平台 (AGP 9.0 极简配置，不写 sourceSets 避免 DSL 报错)
    androidLibrary {
        namespace = "org.forge.kmp.uniffi.rust"
        compileSdk = 36
        minSdk = 34
    }

    jvm()
    iosX64()
    iosArm64()

    sourceSets {
        val commonMain by getting

        val androidMain by getting {
            // 【降维打击】把生成的代码直接挂载给 Android 叶子节点
            kotlin.srcDir(generatedKotlinDir)
            dependencies {
                implementation(libs.jna.get().toString()) {
                    artifact {
                        type = "aar"
                    }
                }
            }
        }

        val jvmMain by getting {
            // 【降维打击】把生成的代码也直接挂载给 Desktop 叶子节点
            kotlin.srcDir(generatedKotlinDir)
            resources.srcDir(generatedJvmResourcesDir)
            dependencies {
                implementation(libs.jna)
            }
        }

        val nativeMain by creating {
            dependsOn(commonMain)
        }
        iosArm64Main.get().dependsOn(nativeMain)
        iosX64Main.get().dependsOn(nativeMain)
    }
}

/* ==========================================================================
 * 3. 宿主机 (Mac/Linux/Win) 目标环境判断
 * ========================================================================== */
fun hostTarget(): String = when {
    OperatingSystem.current().isMacOsX -> {
        val arch = System.getProperty("os.arch").lowercase()
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            "aarch64-apple-darwin"
        } else {
            "x86_64-apple-darwin"
        }
    }
    OperatingSystem.current().isLinux -> "x86_64-unknown-linux-gnu"
    OperatingSystem.current().isWindows -> "x86_64-pc-windows-msvc"
    else -> error("Unsupported OS")
}

fun hostLibName(): String = when {
    OperatingSystem.current().isMacOsX -> "librust_core.dylib"
    OperatingSystem.current().isLinux -> "librust_core.so"
    OperatingSystem.current().isWindows -> "rust_core.dll"
    else -> error("Unsupported OS")
}

// 极简 ABI 定义，配合 cargo-ndk 使用
data class RustAndroidTarget(val androidAbi: String)
val androidTargets = listOf(
    RustAndroidTarget("arm64-v8a")
    // 未来按需解开：RustAndroidTarget("armeabi-v7a"), RustAndroidTarget("x86_64")
)

/* ==========================================================================
 * 4. 自定义 Gradle 任务 (Cargo, cargo-ndk, UniFFI Bindgen)
 * ========================================================================== */

// 4.1 编译宿主机架构 (用于桌面端运行和提取 Bindgen 接口)
tasks.register<Exec>("buildRustHost") {
    group = "rust"
    description = "Build Rust host library for bindgen & desktop"

    inputs.dir(rustProjectDir.resolve("src"))
    inputs.file(rustProjectDir.resolve("Cargo.toml"))
    outputs.dir(rustTargetDir)

    workingDir = rustProjectDir
    executable = cargoPath
    args("build", "--release", "--target", hostTarget())
}

// 4.2 复制宿主机动态库到桌面端资源目录
tasks.register<Copy>("copyRustHostResources") {
    group = "rust"
    dependsOn("buildRustHost")

    from(rustTargetDir.resolve("${hostTarget()}/release/${hostLibName()}"))
    into(generatedJvmResourcesDir)
}

// 4.3 生成 Kotlin 接口代码
tasks.register<Exec>("generateKotlinBindings") {
    group = "rust"
    dependsOn("buildRustHost")

    inputs.file(rustProjectDir.resolve("uniffi.toml"))
    inputs.file(rustTargetDir.resolve("${hostTarget()}/release/${hostLibName()}"))
    outputs.dir(generatedKotlinDir)

    workingDir = rustProjectDir
    executable = cargoPath
    args(
        "run", "--bin", "uniffi-bindgen", "--",
        "generate",
        "--library", "${rustTargetDir.absolutePath}/${hostTarget()}/release/${hostLibName()}",
        "--language", "kotlin",
        "--config", "uniffi.toml",
        "--out-dir", generatedKotlinDir.absolutePath
    )
}

// 4.4 极简无忧的 Android NDK 编译流程 (基于 cargo-ndk)
androidTargets.forEach { target ->
    val buildTaskName = "buildRustAndroid_${target.androidAbi}"

    tasks.register<Exec>(buildTaskName) {
        group = "rust"
        description = "Build Rust for ${target.androidAbi} using cargo-ndk"

        inputs.dir(rustProjectDir.resolve("src"))
        inputs.file(rustProjectDir.resolve("Cargo.toml"))
        outputs.dir(jniLibsDir.resolve(target.androidAbi))

        workingDir = rustProjectDir
        executable = cargoPath

        // 借助 cargo-ndk，省去所有路径拼接和 Copy 任务
        args(
            "ndk",
            "-t", target.androidAbi,
            "-o", jniLibsDir.absolutePath,
            "build",
            "--release"
        )
        environment("ANDROID_NDK_HOME", ndkHome)
    }
}

// 4.5 聚合任务
tasks.register("buildRustAndroid") {
    group = "rust"
    description = "Build all Android ABIs via cargo-ndk"
    dependsOn(androidTargets.map { "buildRustAndroid_${it.androidAbi}" })
}

/* ==========================================================================
 * 5. 生命周期自动化挂载与 Clean 强迫症清理
 * ========================================================================== */

// 不管是点 AS 的绿三角、Make Project 还是命令行编译
// 直接拦截所有带 compile 或 preBuild 字眼的早期核心任务
tasks.configureEach {
    val tName = name
    // 如果任务是编译、打包、资源处理、预构建的任何一种
    if (tName.startsWith("compile") ||
        tName.startsWith("preBuild") ||
        tName.startsWith("merge") ||
        tName.startsWith("package") ||
        tName.startsWith("process")
    ) {
        // Kotlin 必须先有代码才能编
        dependsOn("generateKotlinBindings")
        // 如果是跟 Android 或者 JNI 相关的任务流，必定先生出 .so 文件
        if (tName.contains("Android") || tName.contains("JniLib") || tName.contains("Debug") || tName.contains("Release")) {
            dependsOn("buildRustAndroid")
        }

        // 如果是 Desktop
        if (tName.contains("Jvm") || tName.contains("Desktop")) {
            dependsOn("copyRustHostResources")
        }
    }
}


// 【强迫症清理】执行 ./gradlew clean 时，把放在 src 下的生成的 jniLibs 一起删干净
tasks.named<Delete>("clean") {
    delete(jniLibsDir)
}