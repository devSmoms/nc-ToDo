plugins {
    id("com.android.application") version "9.5.2" apply false
    // AGP 9 compiles Kotlin itself (built-in Kotlin); only the Compose compiler plugin is still needed.
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    // Shared, platform independent code (core) is a plain Kotlin/JVM library.
    id("org.jetbrains.kotlin.jvm") version "2.3.21" apply false
    // Desktop app (Windows/Linux) with Compose Multiplatform.
    id("org.jetbrains.compose") version "1.12.2" apply false
}
