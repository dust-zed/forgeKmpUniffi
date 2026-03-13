package org.forge.kmp.uniffi

import org.forge.kmp.uniffi.rust.uniffiEnsureInitialized

actual object RustBridge {
    init {
        uniffiEnsureInitialized()
    }

    actual fun greet(name: String): String = org.forge.kmp.uniffi.rust.greet(name)
    actual fun add(left: ULong, right: ULong): ULong = org.forge.kmp.uniffi.rust.add(left, right)
}