package org.forge.kmp.uniffi

class Greeting {
    private val platform = getPlatform()

    fun greet(): String {
        return RustBridge.greet(getPlatform().name)
    }
}