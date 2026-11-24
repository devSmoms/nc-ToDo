# Drittanbieter-Software in NC Todo

NC Todo selbst steht unter der GNU GPL v3 (oder später), siehe `LICENSE`.
Die App-Datei (APK) enthält folgende fremde Bibliotheken. Ihre Lizenzen sind mit der GPL v3 vereinbar.
Die vollständigen Lizenztexte liegen unter `app/src/main/assets/licenses/` und sind in der App unter
*Einstellungen → Über NC Todo → Open-Source-Bibliotheken* einsehbar.

| Bibliothek | Copyright | Lizenz |
|---|---|---|
| AndroidX: Core, Activity, Lifecycle, WorkManager, Glance, DataStore | The Android Open Source Project | Apache-2.0 |
| Jetpack Compose, Material 3, Material Icons | The Android Open Source Project / Google | Apache-2.0 |
| Kotlin Standard Library, kotlinx.coroutines | JetBrains s.r.o. and Kotlin contributors | Apache-2.0 |
| OkHttp, Okio | Square, Inc. | Apache-2.0 |
| Guava ListenableFuture (über WorkManager) | The Guava Authors | Apache-2.0 |
| Reorderable (`sh.calvin.reorderable`) | Calvin Liang | Apache-2.0 |
| Protocol Buffers Lite (umbenannt in DataStore enthalten) | Google Inc. | BSD-3-Clause |

## Desktop-App (Windows/Linux)

Zusätzlich zu Kotlin, kotlinx.coroutines, OkHttp und Okio (siehe oben) enthält die Desktop-App:

| Bibliothek | Copyright | Lizenz |
|---|---|---|
| FlatLaf | FormDev Software GmbH | Apache-2.0 |
| Compose Multiplatform (Runtime, UI-Grafik, Material Icons, Skiko) – nur für Zustand und Symbole | JetBrains s.r.o. / The Android Open Source Project | Apache-2.0 |
| JNA, JNA Platform | Timothy Wall and contributors | Apache-2.0 (dual-lizenziert mit LGPL-2.1, genutzt unter Apache-2.0) |
| Java-Laufzeitumgebung (im Installer enthalten) | Oracle and/or its affiliates, Eclipse Adoptium | GPL-2.0 mit Classpath Exception |

Die Lizenztexte liegen in der Desktop-App unter `licenses/` (Apache-2.0, GPL-3.0).

Nur zum Testen (nicht in der APK): JUnit 4 (EPL-1.0), kotlin-test (Apache-2.0).

Vollständige Liste aller Abhängigkeiten prüfen:

    gradlew :app:dependencies --configuration releaseRuntimeClasspath
