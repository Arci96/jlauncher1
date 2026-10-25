# JLauncher

Ein Minecraft-Launcher in Java (Swing + FlatLaf, dunkles Design mit grünen Akzenten).

## Seiten

| Seite | Inhalt |
|---|---|
| **Startseite** | Titelbild, großer Spielen-Knopf, Auswahl von Version, Mod-Loader und Profil, Neuigkeiten, Download-Leiste, Schnellzugriff, Server-Status |
| **Spielen** | Alle Profile: spielen, bearbeiten, Ordner öffnen, löschen |
| **Versionen** | Alle Minecraft-Versionen: herunterladen oder als neues Profil anlegen |
| **Mods / Ressourcenpakete / Shader** | Installierte Dateien (ein/aus, entfernen) und Modrinth-Suche mit Ein-Klick-Installation |
| **Server** | Server speichern, Spielerzahl und Ping sehen, direkt beitreten |
| **Welten** | Welten des Profils sichern (ZIP), wiederherstellen, löschen; Backups liegen außerhalb des Profilordners |
| **Screenshots** | Vorschaubilder des aktuellen Profils |
| **Einstellungen** | Standard-RAM, Java, Konto, Datenordner, Minimieren beim Start |
| **Downloads** | Fortschritt, Geschwindigkeit, Status, Abbrechen |
| **Konsole** | Spielausgabe und Fehlermeldungen |

Mod-Loader: Fabric, Quilt, Forge, NeoForge. Konten: Offline und Microsoft.

## Mods, Modpacks, Kopieren

- **Mod-Updates:** Auf der Mods-Seite sucht "Mod-Updates suchen" bei Modrinth neuere Versionen (Abgleich über SHA-1) und installiert sie nach Rückfrage.
- **Modpacks:** Auf der Seite "Spielen" importiert "Modpack importieren" eine `.mrpack`-Datei als neues Profil (Mods werden geladen, Overrides entpackt, nur erlaubte Download-Quellen).
- **Profil kopieren:** "Kopieren" dupliziert ein Profil samt Welten und Mods, z. B. zum Testen neuer Mods.
- **CurseForge:** Neben Modrinth kann auch CurseForge durchsucht werden. Dafür unter *Einstellungen → CurseForge* einen kostenlosen API-Schlüssel von https://console.curseforge.com eintragen.
  Mods, deren Autor Downloads durch andere Programme nicht erlaubt, lädt der Launcher nicht; stattdessen gibt es den Link zum manuellen Download.

## FPS-Limit

Unter **Einstellungen** (Standard für neue Profile und für das aktuelle Profil) oder im Profil-Dialog.
Der Launcher schreibt `maxFps` vor dem Start in die `options.txt` des Profils.
`0` = nichts ändern, `260` = unbegrenzt. Mit eingeschaltetem VSync begrenzt zusätzlich die Bildwiederholrate des Monitors.

## Stabilität

- Downloads werden bei Netzwerkfehlern bis zu dreimal wiederholt und per Prüfsumme kontrolliert
- Beschädigte Versionsdateien werden automatisch neu geladen
- Die Konfiguration wird atomar gespeichert; eine defekte Datei wird als `launcher.json.defekt` gesichert
- RAM-Maximum wird auf den vorhandenen Arbeitsspeicher begrenzt; Prüfung auf freien Speicherplatz und vorhandene Java-Datei
- Nur ein Launcher-Fenster gleichzeitig (Sperrdatei)
- Unerwartete Fehler landen in `launcher-crash.log`; bei einem Minecraft-Absturz gibt es einen Hinweis mit Weg zur Konsole und zu den Crash-Reports
- Im Profil-Dialog gibt es einen Knopf "Empfohlen" für bewährte JVM-Argumente (G1GC)

## Bauen und starten

Voraussetzung: **JDK 17 oder neuer** und Maven.

```bash
mvn package
java -jar target/JLauncher.jar
```

Wichtig: Starte `JLauncher.jar`, nicht `original-JLauncher.jar`.
Auf GitHub baut `.github/workflows/build.yml` die JAR automatisch (Tab *Actions* -> *Artifacts*).
Hinweis: Beim Hochladen über die Webseite werden Ordner mit Punkt (`.github`) oft übersprungen,
dann die Datei dort direkt über *Add file -> Create new file* anlegen.

Daten liegen unter `%APPDATA%\.jlauncher` (Windows), `~/Library/Application Support/jlauncher` (macOS)
bzw. `~/.local/share/jlauncher` (Linux).

## Microsoft-Login einrichten

1. https://portal.azure.com -> *App-Registrierungen* -> *Neue Registrierung*
2. Kontotypen: "Konten in einem beliebigen Organisationsverzeichnis und persönliche Microsoft-Konten"
3. *Authentifizierung* -> *Öffentliche Clientflows zulassen* = **Ja**
4. Client-ID im Launcher unter **Konto** eintragen und anmelden

Nach meinem Kenntnisstand verlangt Mojang für neue Azure-Apps eine Freischaltung für die Minecraft-API.
Ohne sie kann der letzte Login-Schritt scheitern. Der Offline-Modus funktioniert immer.

## Bekannte Einschränkungen

- **Nicht gegen die echten Server getestet:** Downloads, Forge/NeoForge-Installer, Microsoft-Login,
  Neuigkeiten (Mojang Patch Notes) und Server-Ping sind ungetestet. Die Oberfläche wurde nur ohne
  FlatLaf in ein Bild gerendert; das echte Aussehen kann leicht abweichen.
- Das Titelbild ist selbst gezeichnet (kein Minecraft-Artwork/-Logo, das gehört Mojang).
- Java wird nur gesucht, nicht heruntergeladen.
- Mod-Updates gibt es nur für Mods von Modrinth (nicht für CurseForge). Modpacks werden nur im Modrinth-Format (.mrpack) importiert, nicht von CurseForge.
- Mod-Updates, Modpack-Import und CurseForge sind ungetestet gegen die echten Server.
- Download-Anzeige: Fortschritt nach Dateianzahl, MB und MB/s sind Näherungswerte. Pause gibt es nicht, nur Abbrechen.
- Server-Ping nutzt keine SRV-Einträge (nur Adresse:Port, Standard 25565).
- NeoForge für 1.20.1 fehlt (altes Versionsschema).
- Shader laufen nur mit Iris oder OptiFine; der Launcher installiert diese nicht automatisch.
- Das Microsoft-Refresh-Token liegt unverschlüsselt in `launcher.json`.
