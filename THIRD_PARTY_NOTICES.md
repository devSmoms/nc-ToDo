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

Nur zum Testen (nicht in der APK): JUnit 4 (EPL-1.0), kotlin-test (Apache-2.0).

Vollständige Liste aller Abhängigkeiten prüfen:

    gradlew :app:dependencies --configuration releaseRuntimeClasspath
