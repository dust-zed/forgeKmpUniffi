package org.forge.kmp.uniffi

import org.forge.kmp.uniffi.rust.uniffiEnsureInitialized

// 使用 actual 关键字实现
actual object RustBridge {

    init {
        // 💡 极其关键：首次加载时唤醒底层 JNA 和动态库！
        uniffiEnsureInitialized()
    }

    // 直接委托给刚才生成的全局函数
    actual fun greet(name: String): String {
        return org.forge.kmp.uniffi.rust.greet(name)
    }

    actual fun add(left: ULong, right: ULong): ULong {
        return org.forge.kmp.uniffi.rust.add(left, right)
    }
}