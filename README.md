# forgeKmpUniffi

一个 Kotlin Multiplatform (KMP) + Rust + UniFFI 的示例项目，展示了如何在跨平台应用中集成 Rust 代码。目前支持 Android 和 Desktop (JVM) 平台。

## 技术栈

### 前端
- **Kotlin Multiplatform** 2.3.0
- **Compose Multiplatform** 1.10.0
- **Compose Material3**

### 后端
- **Rust** (2024 edition)
- **UniFFI** 0.31 - Mozilla 的跨语言 FFI 框架
- **JNA** 5.18.1 - Java Native Access

### 构建工具
- **Gradle** 9.4.0
- **Android Gradle Plugin** 8.11.2
- **cargo-ndk** - Android NDK 构建工具

## 项目结构

```
forgeKmpUniffi/
├── composeApp/                 # 主应用模块
│   ├── build.gradle.kts       # 应用构建配置
│   └── src/
│       ├── commonMain/        # 共享 Kotlin 代码
│       │   └── kotlin/
│       │       ├── App.kt            # Compose UI
│       │       ├── Greeting.kt       # 示例业务逻辑
│       │       ├── Platform.kt       # 平台抽象接口
│       │       └── RustBridge.kt     # Rust 桥接 expect 声明
│       ├── androidMain/       # Android 特定实现
│       │   └── kotlin/
│       │       ├── MainActivity.kt
│       │       ├── Platform.android.kt
│       │       └── RustBridge.android.kt
│       ├── jvmMain/           # Desktop (JVM) 特定实现
│       │   └── kotlin/
│       │       ├── main.kt
│       │       ├── Platform.jvm.kt
│       │       └── RustBridge.jvm.kt
│       └── iosMain/           # iOS 特定实现 (暂未集成 Rust)
│
├── rustBridge/                 # Rust 构建桥接模块
│   ├── build.gradle.kts       # Rust 构建和绑定生成配置
│   └── src/
│       └── androidMain/jniLibs/  # 编译后的 Android .so 库
│
├── rust-core/                  # Rust 核心库
│   ├── Cargo.toml             # Rust 依赖配置
│   ├── uniffi.toml            # UniFFI 配置
│   └── src/
│       ├── lib.rs             # Rust 源码 (UniFFI 导出)
│       └── bin/
│           └── uniffi-bindgen.rs  # UniFFI 绑定生成器
│
├── iosApp/                     # iOS 原生应用包装
│   └── iosApp/
│       ├── iOSApp.swift
│       └── ContentView.swift
│
├── gradle/
│   └── libs.versions.toml     # 版本目录
├── build.gradle.kts           # 根项目构建配置
├── settings.gradle.kts        # 项目设置
└── local.properties           # 本地配置 (SDK 路径等)
```

## 环境要求

### 必需
- **JDK** 17+
- **Android Studio** (最新版本)
- **Android SDK** (compileSdk 36, minSdk 34)
- **Android NDK** (推荐 29.0+)
- **Rust** (2024 edition)
- **cargo-ndk**: `cargo install cargo-ndk`

### 配置 local.properties

```properties
sdk.dir=/path/to/Android/sdk
ndk.dir=/path/to/Android/sdk/ndk/29.0.xxxxx
cargo.path=/path/to/.cargo/bin/cargo
```

## 快速开始

### 1. 克隆项目

```bash
git clone <repository-url>
cd forgeKmpUniffi
```

### 2. 配置本地环境

创建或修改 `local.properties` 文件，配置 SDK 和 NDK 路径。

### 3. 构建运行

**Android:**
```bash
./gradlew :composeApp:installDebug
```

**Desktop:**
```bash
./gradlew :composeApp:run
```

## Rust 示例功能

`rust-core/src/lib.rs` 中定义了以下示例函数：

```rust
// 简单的问候函数
#[uniffi::export]
pub fn greet(name: String) -> String {
    format!("Hello, {}! Welcome to Rust.", name)
}

// 加法运算
#[uniffi::export]
pub fn add(left: u64, right: u64) -> u64 {
    left + right
}

// 数据结构定义
#[derive(uniffi::Record)]
pub struct Person {
    pub name: String,
    pub age: u32,
}

// 创建 Person 实例
#[uniffi::export]
pub fn create_person(name: String, age: u32) -> Person {
    Person { name, age }
}
```

## Kotlin 调用示例

```kotlin
// 通过 RustBridge 调用 Rust 函数
val greeting = RustBridge.greet("World")  // "Hello, World! Welcome to Rust."
val sum = RustBridge.add(10u, 20u)        // 30

// 或直接使用生成的绑定
import org.forge.kmp.uniffi.rust.*

val person = create_person("Alice", 30)
println("${person.name} is ${person.age} years old")
```

## 工作原理

### 构建流程

```
Gradle 构建触发
       │
       ▼
┌──────────────────┐
│  buildRustHost   │  编译 Rust (Host 目标)
│  cargo build     │  用于 Desktop 和绑定生成
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│generateKotlinBind│  生成 Kotlin FFI 绑定
│ uniffi-bindgen   │  → rust_core.kt (~1000+ 行)
└────────┬─────────┘
         │
    ┌────┴────┐
    ▼         ▼
┌───────┐  ┌───────┐
│Android│  │Desktop│
│ Build │  │ Build │
└───┬───┘  └───┬───┘
    │          │
    ▼          ▼
┌───────┐  ┌───────┐
│.so文件│  │.dylib │
│(arm64)│  │(host) │
└───────┘  └───────┘
```

### 架构设计

```
┌─────────────────────────────────────────────────┐
│                  Compose UI                      │
│              (commonMain - 共享)                 │
├─────────────────────────────────────────────────┤
│                RustBridge                        │
│           (expect/actual 模式)                   │
├──────────────────┬──────────────────────────────┤
│   Android        │        Desktop (JVM)         │
│   JNI + JNA      │           JNA                │
├──────────────────┴──────────────────────────────┤
│              Generated Bindings                  │
│         (UniFFI 生成的 Kotlin 代码)             │
├─────────────────────────────────────────────────┤
│              Rust Core Library                   │
│         (librust_core.so / .dylib)              │
└─────────────────────────────────────────────────┘
```

### Expect/Actual 模式

项目使用 Kotlin 的 expect/actual 机制实现跨平台 Rust 调用：

**commonMain/RustBridge.kt (expect):**
```kotlin
expect object RustBridge {
    fun greet(name: String): String
    fun add(left: ULong, right: ULong): ULong
}
```

**androidMain/RustBridge.android.kt (actual):**
```kotlin
import org.forge.kmp.uniffi.rust.uniffiEnsureInitialized

actual object RustBridge {
    init {
        uniffiEnsureInitialized()  // 初始化 JNA
    }

    actual fun greet(name: String): String =
        org.forge.kmp.uniffi.rust.greet(name)

    actual fun add(left: ULong, right: ULong): ULong =
        org.forge.kmp.uniffi.rust.add(left, right)
}
```

## 自动化构建

项目配置了自动依赖关系，任何编译任务都会自动触发 Rust 构建：

```kotlin
// rustBridge/build.gradle.kts
tasks.configureEach {
    if (name.startsWith("compile") || name.startsWith("preBuild") ||
        name.startsWith("merge") || name.startsWith("package")) {
        dependsOn("generateKotlinBindings")

        if (name.contains("Android")) {
            dependsOn("buildRustAndroid")
        }
        if (name.contains("Jvm") || name.contains("Desktop")) {
            dependsOn("copyRustHostResources")
        }
    }
}
```

## 注意事项

1. **首次构建**: 需要编译 Rust 库，可能需要较长时间
2. **NDK 版本**: 确保 NDK 版本与配置一致
3. **Git 忽略**: 编译产物 (`jniLibs/`, `*.dylib` 等) 已被 `.gitignore` 忽略
4. **iOS 支持**: iOS 平台的 Rust 集成尚未完成
5. **Android ABI**: 目前仅支持 `arm64-v8a`，可根据需要添加其他架构

## 扩展 Android ABI

如需支持更多 Android 架构，修改 `rustBridge/build.gradle.kts`:

```kotlin
val androidAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
```

## 添加新的 Rust 函数

1. 在 `rust-core/src/lib.rs` 中添加函数并使用 `#[uniffi::export]` 标记
2. 重新构建项目，UniFFI 会自动生成对应的 Kotlin 绑定
3. 在 `RustBridge` 中添加 expect/actual 声明

```rust
// rust-core/src/lib.rs
#[uniffi::export]
pub fn your_new_function(param: String) -> String {
    // 你的实现
}
```

```kotlin
// commonMain/RustBridge.kt
expect fun yourNewFunction(param: String): String

// androidMain/RustBridge.android.kt
actual fun yourNewFunction(param: String): String =
    org.forge.kmp.uniffi.rust.your_new_function(param)
```

## 许可证

MIT License

## 参考资料

- [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- [UniFFI](https://mozilla.github.io/uniffi-rs/)
- [cargo-ndk](https://github.com/nickelc/cargo-ndk)
