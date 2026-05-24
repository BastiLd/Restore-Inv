# Restore Inventory

Fabric-Mod fuer Minecraft, die das Spieler-Inventar regelmaessig sichert und
spaeter wiederhergestellen kann (z. B. wenn man in Lava faellt).

## Features

- Drei Save-Slots pro Spieler, jeweils mit Ringpuffer der letzten N Saves.
- Auto-Save alle X Minuten in Slot 1 / 2 (konfigurierbar).
- **Auto-Save direkt vor dem Tod** in Slot 3 (`ServerLivingEntityEvents.ALLOW_DEATH`).
- **Inventar-Vorschau**: Klick auf einen Save zeigt Ruestung, Hauptinventar,
  Hotbar und Offhand in einer 9x6-GUI bevor wirklich wiederhergestellt wird.
- **Tooltips mit Zeitstempel**: "Vor 3 Min", "Vor 17 Min" usw. plus Item-Anzahl
  und das beste Tool ("Top-Tool").
- **Pin-Funktion**: Rechtsklick auf einen Save markiert ihn als geschuetzt.
  Gepinnte Saves werden vom Ringpuffer nicht ueberschrieben.
- **Konfigurierbare Saves pro Slot** (1..9).
- **Restore-Sound** (an/aus).
- **OP-Restore-Pflicht** (an/aus): wenn an, koennen nur OPs Restore-Befehle
  ausfuehren.
- **Per-Spieler-Settings** persistieren (Preview-Toggle).
- **Befehle**: `/restoreInv 1|2|3`, `/restoreInv save`, `/restoreInv config`,
  **`/restoreInv version`**.

## Unterstuetzte Versionen

Die Mod wird fuer mehrere Minecraft-Versionen parallel gebaut. Jede Version
hat ihre eigene JAR im Release.

| Minecraft | Source-Tree         | JAR-Name                                    |
| --------- | ------------------- | ------------------------------------------- |
| 1.21      | `shared/api-old/`   | `RestoreInventory-mc1.21-<version>.jar`     |
| 1.21.1    | `shared/api-old/`   | `RestoreInventory-mc1.21.1-<version>.jar`   |
| 1.21.2    | `shared/api-old/`   | `RestoreInventory-mc1.21.2-<version>.jar`   |
| 1.21.3    | `shared/api-old/`   | `RestoreInventory-mc1.21.3-<version>.jar`   |
| 1.21.4    | `shared/api-old/`   | `RestoreInventory-mc1.21.4-<version>.jar`   |
| 1.21.9    | `shared/api-new/`   | `RestoreInventory-mc1.21.9-<version>.jar`   |
| 1.21.10   | `shared/api-new/`   | `RestoreInventory-mc1.21.10-<version>.jar`  |
| 1.21.11   | `shared/api-new/`   | `RestoreInventory-mc1.21.11-<version>.jar`  |

Die Luecke 1.21.5 bis 1.21.8 wird derzeit nicht unterstuetzt - dort gibt es
mehrere harte API-Brueche (vor allem im NBT-/Storage-API), die einen eigenen
Source-Tree erfordern wuerden. Bei Bedarf kann das nachgezogen werden.

## Repo-Struktur

```
.
├── shared/
│   ├── api-old/               # Quelltext fuer Minecraft 1.21.x mit alter API
│   │   ├── src/main/java/...
│   │   └── src/main/resources/
│   └── api-new/               # Quelltext fuer Minecraft 1.21.9+ (neue API)
├── versions/
│   ├── 1.21/                  # Subprojekt (gradle.properties + build.gradle)
│   ├── 1.21.1/
│   ├── 1.21.2/
│   ├── 1.21.3/
│   ├── 1.21.4/
│   ├── 1.21.9/
│   ├── 1.21.10/
│   └── 1.21.11/
├── gradle/
│   ├── mod-build.gradle       # gemeinsame Build-Logik fuer alle Subprojekte
│   └── shared-sources.gradle  # Verbindet Subprojekt mit shared/<tree>/
├── build.gradle               # sammelt alle JARs in build/libs/
├── settings.gradle            # entdeckt versions/* automatisch
└── gradle.properties          # globale Mod-Metadaten (mod_version, group, ...)
```

## Build

Multi-Version-Build mit einem Befehl (alle 8 Versionen parallel):

```bash
./gradlew build           # Linux / macOS / Git Bash
.\gradlew.bat build       # Windows
```

JARs liegen am Ende sowohl in `build/libs/` (alle Versionen gesammelt) als
auch unter `versions/<mc>/build/libs/` (pro Version).

Nur eine Version bauen:

```bash
./gradlew :mc-1.21.11:build
./gradlew :mc-1.21.1:build
```

Voraussetzung: JDK 21. Gradle holt sich sonst per Toolchain ein passendes.

## Mod-Version erhoehen

```bash
python bump_version.py            # patch +1
python bump_version.py minor      # 2.3.0 -> 2.4.0
python bump_version.py major      # 2.3.0 -> 3.0.0
python bump_version.py 3.1.4      # explizit
python bump_version.py patch --tag      # zusaetzlich Git-Tag v<version>
python bump_version.py patch --commit   # Commit + Tag in einem Schritt
```

Anschliessend `git push --follow-tags` -- dann baut die GitHub-Action und
haengt **alle JARs aller Versionen** ans Release.

## Fabric-Versionen aktualisieren

```bash
python update_fabric.py            # alle Subprojekte aktualisieren
python update_fabric.py 1.21.11    # nur eine Version
python update_fabric.py --dry-run
```

## In-Game-Befehle

| Befehl                   | Wirkung                                                     |
| ------------------------ | ----------------------------------------------------------- |
| `/restoreInv 1\|2\|3`    | Stellt das Inventar aus dem entsprechenden Slot wieder her  |
| `/restoreInv save`       | Speichert das Inventar in Slot 3                            |
| `/restoreInv config`     | Oeffnet die Config-GUI                                      |
| `/restoreInv version`    | Zeigt Mod- und MC-Version                                   |

## Release-Workflow

1. `python bump_version.py minor --commit`
2. `git push --follow-tags`
3. GitHub-Action baut alle Versionen und legt das Release samt JARs an.

## Lokales JDK setzen (optional)

Falls Gradle dein JDK nicht findet, lege `gradle-local.properties` an:

```properties
org.gradle.java.home=C:\\Program Files\\Java\\jdk-21
```

Die Datei ist via `.gitignore` ausgeschlossen.
