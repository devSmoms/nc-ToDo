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

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    // Windows: protect secrets with DPAPI (Apache-2.0 / LGPL-2.1 dual licensed; used under Apache-2.0).
    implementation("net.java.dev.jna:jna-platform:5.17.0")
}

compose.desktop {
    application {
        mainClass = "de.devsmoms.nctodo.desktop.MainKt"
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
