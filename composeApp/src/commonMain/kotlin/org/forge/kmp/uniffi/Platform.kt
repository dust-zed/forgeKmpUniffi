package org.forge.kmp.uniffi

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform