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
| **Screenshots** | Vorschaubilder des aktuellen Profils |
| **Einstellungen** | Standard-RAM, Java, Konto, Datenordner, Minimieren beim Start |
| **Downloads** | Fortschritt, Geschwindigkeit, Status, Abbrechen |
| **Konsole** | Spielausgabe und Fehlermeldungen |

Mod-Loader: Fabric, Quilt, Forge, NeoForge. Konten: Offline und Microsoft.

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
- Download-Anzeige: Fortschritt nach Dateianzahl, MB und MB/s sind Näherungswerte. Pause gibt es nicht, nur Abbrechen.
- Server-Ping nutzt keine SRV-Einträge (nur Adresse:Port, Standard 25565).
- NeoForge für 1.20.1 fehlt (altes Versionsschema).
- Shader laufen nur mit Iris oder OptiFine; der Launcher installiert diese nicht automatisch.
- Das Microsoft-Refresh-Token liegt unverschlüsselt in `launcher.json`.
