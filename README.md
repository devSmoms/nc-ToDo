# NC Todo

Android-App für Nextcloud-Aufgaben (CalDAV/VTODO), bedient sich ähnlich wie Microsoft To Do.

## Funktionen

- Anmeldung per CalDAV (Server-URL, Benutzername, App-Passwort), automatische Erkennung von `/remote.php/dav` bzw. `/.well-known/caldav`
- **Mehrere Konten**, auch auf verschiedenen Servern: Tipp auf Avatar oder Namen oben im Menü öffnet die Kontoauswahl (wechseln, hinzufügen, abmelden). Jedes Konto hat eigene Listen, Offline-Daten und Einstellungen. Beim Abmelden werden alle Daten dieses Kontos vom Gerät gelöscht. Widgets zeigen ein wählbares Konto, das Schnell-Anlegen bietet die Listen aller Konten an.
- Intelligente Listen: **Mein Tag** (heute fällig oder überfällig), **Wichtig** (Stern), **Geplant** (mit Fälligkeitsdatum), **Aufgaben** (alle)
- Eigene Listen anlegen, umbenennen, einfärben, löschen
- Listen teilen mit **Nextcloud-Benutzern** des eigenen Servers: **Nur lesen** oder **Kann bearbeiten**, umschaltbar, entfernbar
- Mit dir geteilte Listen werden angezeigt (schreibgeschützte mit Schloss-Symbol), du kannst sie auch verlassen
- Aufgaben: abhaken, Stern, Fälligkeitsdatum, „Zu Mein Tag hinzufügen“, Notizen, **Schritte** (Unteraufgaben, kompatibel mit Nextcloud Tasks)
- Beim Hinzufügen erscheinen Schnelloptionen: **Mein Tag**, **Fälligkeit** (Heute, Morgen, Nächste Woche, Datum) und **Wichtig**
- Aufgaben **per Wischen nach links oder rechts löschen**, mit „Rückgängig“ in der Hinweisleiste
- **Listen-Symbole**: einfarbige Icons in der Listenfarbe (29 zur Auswahl). Gespeichert als Emoji am Anfang des Namens (z. B. „🛒 Einkauf“), dadurch per CalDAV synchronisiert; in der App immer als einfarbiges Icon angezeigt, fremde Emojis ohne passendes Icon werden ausgeblendet
- **Startbildschirm** einstellbar: zuletzt geöffnet, eine intelligente Liste oder eine bestimmte Liste
- **Widget für den Startbildschirm**: zeigt die offenen Aufgaben einer wählbaren Liste oder Ansicht (Mein Tag, Wichtig, …), Abhaken direkt im Widget, „+“ öffnet das Schnell-Anlegen, Tippen öffnet die Aufgabe in der App. Liste per langem Druck → „Widget anpassen“ änderbar (Android 12+). Option „Tippen öffnet immer die App“: jeder Tipp aufs Widget öffnet die Liste in der App.
- **Kachel „Neue Aufgabe“ in den Schnelleinstellungen** (Menü von oben herunterwischen): öffnet ein kleines Fenster zum Anlegen mit Listenauswahl, Mein Tag, Fälligkeit und Wichtig. Bei gesperrtem Gerät muss zuerst entsperrt werden. Einrichten: Schnelleinstellungen aufklappen → Stift/Bearbeiten → Kachel „Neue Aufgabe“ in den aktiven Bereich ziehen.
- **Fälligkeit mit Uhrzeit** oder ganztägig; in den Einstellungen wählbar, ob neue Fälligkeiten standardmäßig ganztägig sind
- **Wiederholung**: täglich, werktags, wöchentlich, monatlich, jährlich oder „alle N Tage/Wochen/Monate/Jahre“ (RRULE). Beim Abhaken springt die Aufgabe auf den nächsten Termin, Schritte werden zurückgesetzt und ein erledigter Eintrag bleibt als Verlauf – wie bei Microsoft To Do
- **Links**: beliebig viele pro Aufgabe (gespeichert als ATTACH), angezeigt als Vorschaukarte mit Titel und Bild; Link aus der Zwischenablage wird vorgeschlagen. Vorschau abschaltbar
- **Zuständige Person**: eine Person aus der Freigabe der Liste (gespeichert als ATTENDEE ohne ORGANIZER – Nextcloud verschickt dadurch keine Einladungs-Mails)
- **Listenbeschreibung** (calendar-description), unter dem Listentitel angezeigt
- **Deck-Listen ausblenden**: Listen, die Nextcloud Deck für seine Boards anlegt („Deck: …“), werden standardmäßig überall ausgeblendet (abschaltbar)
- **Papierkorb** (Nextcloud 22+): gelöschte Aufgaben und Listen wiederherstellen oder endgültig löschen
- Zum Aktualisieren nach unten ziehen; Änderungen erscheinen sofort und werden im Hintergrund gespeichert
- **Offline nutzbar**: alle Listen und Aufgaben liegen verschlüsselt auf dem Gerät. Aufgaben anlegen, bearbeiten, abhaken und löschen geht auch ohne Netz; die Änderungen werden übertragen, sobald wieder eine Verbindung besteht (auch bei geschlossener App). Bei gleichzeitigen Änderungen werden nur die selbst geänderten Felder auf die Server-Version übertragen, Änderungen anderer bleiben erhalten.
- **Einstellungen**: Design (System/Hell/Dunkel), Synchronisierung beim Öffnen (immer / nach einstellbarer Pause / nie), Intervall bei geöffneter App und im Hintergrund, Benachrichtigungen (tägliche Erinnerung an fällige Aufgaben mit Uhrzeit, Änderungen anderer in geteilten Listen), Abmelden

> Inoffizielle App – kein Produkt der Nextcloud GmbH und nicht mit Microsoft verbunden. „Nextcloud“ ist eine Marke der Nextcloud GmbH.

> **Beta:** Diese App wurde mit Unterstützung von KI entwickelt. Sie wird ohne Gewährleistung bereitgestellt und befindet sich im Beta-Stadium – Fehler und Abstürze sind möglich. Rückmeldungen gerne über *Issues*.

## Download

Fertige APKs gibt es unter **Releases** (rechts auf der GitHub-Seite). Installation: APK auf dem Handy öffnen und „Aus dieser Quelle installieren“ erlauben.

## Bauen

1. Ordner in Android Studio öffnen (*File → Open*), Gradle-Sync abwarten.
   Falls Android Studio Updates für das Android-Gradle-Plugin/Kotlin vorschlägt, kannst du sie übernehmen.
2. *Build → Generate Signed App Bundle / APK → APK* für eine Release-Version, oder direkt auf dem Handy starten (▶).
3. Unit-Tests: `app/src/test/.../CoreTest.kt` (Parser/Protokoll) und `SyncTest.kt` (Offline-Sync mit simuliertem Server) – Rechtsklick → Run.

Voraussetzungen: Android 8.0+, Nextcloud mit gültigem HTTPS-Zertifikat (z. B. Let's Encrypt).

## Sicherheit

- **Zugangsdaten** werden mit AES-256-GCM verschlüsselt, mit einem eigenen Schlüssel pro Konto. Der Schlüssel liegt im Android Keystore (Hardware/StrongBox, falls vorhanden) und kann nicht ausgelesen oder exportiert werden. Die verschlüsselten Daten liegen im privaten App-Speicher.
- **Kein Backup**: `allowBackup=false` und Data-Extraction-Rules verhindern, dass Daten per Cloud-Backup oder Geräteumzug das Gerät verlassen.
- **Nur HTTPS**, TLS 1.2+, keine Klartext-Verbindungen, keine Weiterleitung von HTTPS auf HTTP, nur System-Zertifizierungsstellen (keine vom Nutzer installierten CAs).
- Das Passwort wird **nur an den angemeldeten Server** geschickt; Verweise des Servers auf fremde Adressen werden abgelehnt.
- XML-Antworten werden gegen XXE/Entity-Angriffe gehärtet geparst, Eingaben in XML und iCalendar werden escaped (keine Injection über Titel oder Notizen).
- Bearbeitungen nutzen ETags (`If-Match`), damit gleichzeitige Änderungen anderer nicht überschrieben werden.
- Unbekannte iCalendar-Daten (Erinnerungen, Wiederholungen, Felder anderer Apps) bleiben beim Bearbeiten erhalten.
- Login-Bildschirm ist vor Screenshots und Bildschirmaufnahmen geschützt (`FLAG_SECURE`), nichts wird geloggt.
- Der **Offline-Speicher** (Listen, Aufgaben, Warteschlange) liegt ebenfalls AES-256-GCM-verschlüsselt mit eigenem Keystore-Schlüssel im privaten, nicht gesicherten App-Speicher.
- Benachrichtigungen zeigen auf dem Sperrbildschirm nur die Anzahl, keine Aufgabentitel.
- Die Schnelleinstellungs-Kachel verlangt bei gesperrtem Gerät zuerst das Entsperren. Hinweis: Das Widget zeigt Aufgabentitel auf dem Startbildschirm – wer das nicht möchte, platziert es nicht.
- Beim Abmelden werden Zugangsdaten, Offline-Daten, Schlüssel, geplante Hintergrundjobs und Benachrichtigungen gelöscht. Bei einem Kontowechsel sieht das neue Konto nie den Cache des alten.

**Empfehlung:** In Nextcloud unter *Einstellungen → Sicherheit → Geräte & Sitzungen* ein eigenes App-Passwort erstellen. Es lässt sich jederzeit widerrufen und ist bei Zwei-Faktor-Anmeldung ohnehin nötig.

## Grenzen dieser Version

- Listen anlegen, umbenennen, löschen und teilen geht nur online.
- Hintergrund-Synchronisierung: Android erlaubt höchstens alle 15 Minuten, der genaue Zeitpunkt kann sich zum Akkusparen verschieben. Benachrichtigungen über Änderungen anderer kommen deshalb nicht in Echtzeit (Nextcloud bietet für CalDAV kein Push).
- Erinnerungen (VALARM) einzelner Aufgaben werden nicht angezeigt, bleiben aber erhalten; es gibt die tägliche Sammel-Erinnerung.
- Wiederkehrende Aufgaben werden angezeigt, beim Abhaken aber wie in Nextcloud Tasks komplett erledigt.
- Teilen funktioniert mit Nextcloud (oc:share-Erweiterung), nicht mit beliebigen CalDAV-Servern. Gruppen-Freigaben, die z. B. im Nextcloud-Web angelegt wurden, werden angezeigt und lassen sich entfernen.
- Die Benutzersuche beachtet die Freigabe-Einstellungen des Servers; notfalls den Benutzernamen direkt eingeben.

## Aufbau

```
app/src/main/java/de/devsmoms/nctodo/
  ical/   iCalendar-Parser/Serializer, VTODO-Mapping
  dav/    WebDAV/CalDAV-XML, HTTP-Client, Principal-Helfer
  data/   CalDAV-Zugriff, Modelle, verschlüsselte Zugangsdaten + Offline-Speicher, Einstellungen
  sync/   Offline-Sync-Engine (Warteschlange, Konfliktauflösung), WorkManager-Jobs, Benachrichtigungen
  ui/     Compose-Oberfläche (Login, Listen, Details, Teilen)
```

## Release veröffentlichen (für Maintainer)

1. In `app/build.gradle.kts` `versionCode` (+1) und `versionName` erhöhen, committen.
2. Tag setzen und pushen, z. B. `git tag -a v2.0.3 -m "Version 2.0.3"` und `git push --follow-tags`.
3. Die GitHub Action „Release APK“ baut die signierte APK und hängt sie an das Release.

Benötigte Repository-Secrets (*Settings → Secrets and variables → Actions*):
`SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.

## Lizenz

Copyright (C) 2026 devSmoms

Dieses Programm ist freie Software: Sie können es unter den Bedingungen der GNU General Public License, wie von der Free Software Foundation veröffentlicht, weitergeben und/oder modifizieren, entweder gemäß Version 3 der Lizenz oder (nach Ihrer Wahl) jeder späteren Version.

Die Veröffentlichung dieses Programms erfolgt in der Hoffnung, dass es Ihnen von Nutzen sein wird, aber OHNE IRGENDEINE GARANTIE, sogar ohne die implizite Garantie der MARKTREIFE oder der VERWENDBARKEIT FÜR EINEN BESTIMMTEN ZWECK. Details finden Sie in der GNU General Public License.

Den vollständigen Lizenztext finden Sie in der Datei [LICENSE](LICENSE).

Verwendete Bibliotheken (AndroidX, Jetpack Compose, Glance, WorkManager, OkHttp, Material Icons) stehen unter der Apache License 2.0, die mit der GPL-3.0 vereinbar ist.
