import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// NC Todo for Windows and Linux. Shares all CalDAV/sync logic with Android via :core.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

val appVersion = providers.gradleProperty("nctodo.versionName").get()

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Writes the version into the resources so the app can show it under "Über NC Todo".
val versionResource = tasks.register("generateVersionResource") {
    val out = layout.buildDirectory.dir("generated/version")
    val version = appVersion
    inputs.property("version", version)
    outputs.dir(out)
    doLast {
        out.get().file("nctodo-version.txt").asFile.apply { parentFile.mkdirs() }.writeText(version)
    }
}
sourceSets.main { resources.srcDir(versionResource) }

dependencies {
    implementation(project(":core"))
    // UI: Swing with FlatLaf (sharp system text rendering). From Compose only the snapshot state
    // (used by AppState) and the Material icons, drawn into Swing as vectors.
    implementation("com.formdev:flatlaf:3.6.1")
    implementation(compose.desktop.currentOs)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    // Windows: protect secrets with DPAPI (Apache-2.0 / LGPL-2.1 dual licensed; used under Apache-2.0).
    implementation("net.java.dev.jna:jna-platform:5.17.0")
}

compose.desktop {
    application {
        mainClass = "de.devsmoms.nctodo.desktop.MainKt"
        // FlatLaf loads its native library (window title bar on Windows).
        jvmArgs += listOf("--enable-native-access=ALL-UNNAMED")
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            packageName = "NC Todo"
            packageVersion = appVersion
            description = "Nextcloud-Aufgaben im Stil von Microsoft To Do"
            vendor = "devSmoms"
            copyright = "Copyright (C) 2026 devSmoms. GPL-3.0-or-later."
            licenseFile.set(rootProject.file("LICENSE"))
            // Smaller and safer than bundling the whole JDK; covers TLS, XML and JNA.
            modules("java.naming", "java.xml", "jdk.crypto.ec", "java.net.http", "jdk.unsupported")
            windows {
                iconFile.set(project.file("icons/icon.ico"))
                menuGroup = "NC Todo"
                shortcut = true
                perUserInstall = true
                dirChooser = true
                // Fixed id so newer MSI versions replace older ones. Never change it.
                upgradeUuid = "6a1b4f0e-5c1d-4b6e-9e2a-3d8f0c7b2a41"
            }
            linux {
                iconFile.set(project.file("icons/icon.png"))
                packageName = "nc-todo"
                debMaintainer = "devSmoms <devsmoms@users.noreply.github.com>"
                menuGroup = "Office"
                appCategory = "Office"
                shortcut = true
            }
        }
        buildTypes.release.proguard {
            // Not needed for a desktop app; avoids reflection issues (JNA, OkHttp).
            isEnabled.set(false)
        }
    }
}
