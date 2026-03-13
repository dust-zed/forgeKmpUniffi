package org.forge.kmp.uniffi

// 这是一个多端共享的单例桥接器
expect object RustBridge {
    fun greet(name: String): String
    fun add(left: ULong, right: ULong): ULong
}