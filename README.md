# FA Mail – Phase 1

Android-E-Mail-App im Apple-Mail-Design für das Samsung Galaxy Z Fold 7.
Kotlin · Jetpack Compose · Room · DataStore · WorkManager · JavaMail (IMAP/SMTP) · Android PdfRenderer.

> **Wichtig:** Dieses Projekt wurde in einer Umgebung ohne Zugang zu den Android-Paketquellen erstellt.
> Der Code wurde auf Syntax geprüft, aber **noch nicht vollständig kompiliert oder auf einem Gerät getestet**.
> Beim ersten Build in Android Studio oder GitHub Actions können einzelne Compiler-Meldungen auftreten –
> diese bitte zurückmelden, dann werden sie korrigiert.

---

## 1. APK bauen

### Variante A – GitHub (ohne eigenen PC-Aufbau)
1. Neues, leeres Repository anlegen, z. B. `FA-Mail-Android`.
2. Den Inhalt dieses Ordners hochladen (inkl. `.github/workflows/build-apk.yml`).
3. Unter **Actions → „Debug-APK bauen“** läuft der Build automatisch.
4. Nach ca. 5–8 Minuten unter dem Lauf das Artefakt **FA-Mail-debug-apk** herunterladen, entpacken, APK auf das Fold kopieren und installieren
   („Installation aus unbekannten Quellen“ einmalig erlauben).

### Variante B – Android Studio
1. Ordner in Android Studio (Ladybug oder neuer) öffnen, Gradle-Sync abwarten.
2. Fold per USB verbinden (Entwickleroptionen → USB-Debugging) und **Run** drücken,
   oder **Build → Build APK(s)**.

Voraussetzungen: JDK 17, Android SDK 35.

---

## 2. Was in Phase 1 funktioniert

| Bereich | Stand |
|---|---|
| Apple-Mail-Design (hell/dunkel), eigene Icons & Avatare | ✅ |
| Aufgeklappt: links Liste, rechts Nachricht – **keine** dauerhafte Ordnerspalte | ✅ |
| Postfachübersicht nur über das Symbol oben links, schließt nach Auswahl | ✅ |
| Verschiebbare Trennlinie 20–80 %, Doppeltipp = 40/60, Position wird gespeichert | ✅ |
| Vollbild-Nachricht (Symbol oben rechts) | ✅ |
| Zugeklappt: Smartphone-Ansicht, Nachricht im Vollbild, Zurück-Geste | ✅ |
| Auf-/Zuklappen ohne Neustart der Activity (Zustand, Scrollposition, Entwurf bleiben) | ✅ |
| Mehrere Konten, gemeinsamer Posteingang, einzelne Ordner | ✅ |
| IMAP-Synchronisierung (letzte 250 Nachrichten je Ordner), Flags, Löschabgleich | ✅ |
| Senden, Antworten, Allen antworten, Weiterleiten (inkl. Original-Anhänge), CC/BCC | ✅ |
| Anhänge versenden, öffnen, speichern/teilen | ✅ |
| Lokale Entwürfe (automatisch gespeichert, bleiben bei Sendefehler erhalten) | ✅ |
| Löschen (→ Papierkorb), Archivieren, Verschieben, gelesen/ungelesen, Markieren | ✅ |
| Wischgesten in der Liste, langes Drücken = Kontextmenü | ✅ |
| Suche (Absender, Betreff, Vorschau, Empfänger), Filter, Sortierung | ✅ |
| Sichere HTML-Anzeige: kein JavaScript, externe Bilder/Tracker blockiert | ✅ |
| **Integrierter PDF-Reader** (Zoom, Seitenanzeige, Teilen, Drucken) – kein externes Programm | ✅ (vorgezogen aus Phase 2) |
| Passwörter verschlüsselt (AES-256-GCM, Android Keystore), kein Cloud-Backup | ✅ |
| Hintergrund-Abruf (WorkManager) + Benachrichtigungen, Sperrbildschirm-Schutz | ✅ |
| Kontospezifische Signaturen, Einstellungen (Design, Schrift, Abruf, Benachrichtigungen) | ✅ |

### Bewusst noch NICHT enthalten (keine Schein-Buttons in der App)
- **OAuth-Anmeldung Google/Microsoft** – braucht eigene Client-IDs (siehe Abschnitt 4). Bis dahin: App-Passwort.
- Entwürfe auf dem Server synchronisieren (aktuell nur lokal)
- Eingebettete `cid:`-Bilder in HTML-Mails, Gesprächsansicht (Threads)
- PDF-Formulare, Bearbeiten, Unterschrift, „Als Antwort senden“ → **Phase 2**
- Geschäftliche Kategorien & Regeln → **Phase 3**
- Lokale KI (Zusammenfassung, „Wer wartet auf meine Antwort?“) → **Phase 4**
- Biometrische App-Sperre → Phase 3

---

## 3. Konten einrichten

| Anbieter | Was nötig ist |
|---|---|
| **Gmail** | 2-Faktor aktivieren → myaccount.google.com → Sicherheit → *App-Passwörter* → Passwort erzeugen und in FA Mail eingeben |
| **Yahoo / ymail** | Kontosicherheit → *App-Passwort generieren* |
| **GMX / WEB.DE** | Im Webmail: Einstellungen → *POP3/IMAP-Abruf* → IMAP erlauben, dann normales Passwort |
| **iCloud** | appleid.apple.com → *App-spezifisches Passwort* |
| **Outlook.com / Microsoft 365** | Microsoft erlaubt meist nur OAuth → erst nach Abschnitt 4 nutzbar |
| **Firmen-Adresse** | „Andere“ wählen, IMAP/SMTP-Server und Ports vom Anbieter eintragen |

Beim Tippen auf „Anmelden“ prüft die App IMAP **und** SMTP. Erst wenn beides klappt, wird das Konto gespeichert.

---

## 4. OAuth vorbereiten (für Phase 3)

**Google:** Google Cloud Console → Projekt → *APIs & Dienste* → OAuth-Zustimmungsbildschirm → OAuth-Client-ID vom Typ *Android* (Paketname `de.famail.app`, SHA-1 des Signierschlüssels). Bereich: `https://mail.google.com/`.

**Microsoft:** Azure Portal → *App-Registrierungen* → Neue Registrierung → Plattform *Android* (Paketname + Signatur-Hash). API-Berechtigungen: `IMAP.AccessAsUser.All`, `SMTP.Send`, `offline_access`.

Die Client-IDs werden **nicht** in den Quellcode geschrieben, sondern später über `local.properties` eingebunden.

---

## 5. Technik

- **Architektur:** MVVM, ein `MainViewModel`, `MailRepository` als einzige Datenquelle, Room-Datenbank als Cache (Offline-Lesen).
- **E-Mail:** `com.sun.mail:android-mail` (JavaMail) – IMAP, SMTP, MIME, Zeichensätze, RFC-2231-Dateinamen.
- **Doppelversand-Schutz:** Jeder Entwurf erhält einmalig eine feste Message-ID; der Senden-Knopf ist während des Versands gesperrt; „Gesendet“ erscheint erst nach Bestätigung des SMTP-Servers.
- **PDF:** Android-eigener `PdfRenderer` (kostenlos, keine Fremdbibliothek) für die Anzeige. Für Formulare/Bearbeitung in Phase 2 ist `PDFBox-Android` (Apache-Lizenz) vorgesehen.
- **Foldable:** Ab 600 dp Breite Zweispalten-Layout; die Activity wird beim Auf-/Zuklappen nicht neu erzeugt (`configChanges`), daher gehen keine Eingaben verloren.

---

## 6. Release-APK signieren

```bash
keytool -genkeypair -v -keystore famail-release.jks -keyalg RSA -keysize 4096 -validity 10000 -alias famail
```
Danach in Android Studio: **Build → Generate Signed App Bundle / APK**. Die `.jks`-Datei und Passwörter **niemals** ins Repository legen (steht bereits in `.gitignore`) und sicher aufbewahren – ohne sie sind keine Updates möglich.

---

## 7. Testliste für dich (auf dem Fold 7)

1. Konto hinzufügen (z. B. GMX oder Gmail mit App-Passwort)
2. Posteingang lädt, Nachricht öffnen, HTML-Mail anzeigen, „Laden“ für Bilder
3. Trennlinie ziehen (20/80, 80/20), doppeltippen, App schließen und neu öffnen → Position bleibt
4. Während eine Mail offen ist zu- und aufklappen
5. Antworten, Weiterleiten mit Anhang, Senden ohne Internet → Fehlermeldung, Entwurf bleibt
6. PDF-Anhang antippen → öffnet in FA Mail, Zoom, Teilen, Drucken
7. Löschen, Archivieren, Verschieben, Wischen nach links/rechts
