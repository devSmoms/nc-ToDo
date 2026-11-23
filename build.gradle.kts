plugins {
    id("com.android.application") version "9.5.2" apply false
    // AGP 9 compiles Kotlin itself (built-in Kotlin); only the Compose compiler plugin is still needed.
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}
