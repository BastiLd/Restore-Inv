# Restore Inventory

Fabric-Mod fuer Minecraft, die das Spieler-Inventar regelmaessig sichert und
spaeter wiederhergestellen kann (z. B. wenn man in Lava faellt).

Unterstuetzt mehrere Minecraft-Versionen parallel. Fuer jede unterstuetzte
Version gibt es ein eigenes Subprojekt unter `versions/<mc>/`.

## Unterstuetzte Versionen

| Minecraft | Subprojekt-Pfad      | JAR-Name                                    |
| --------- | -------------------- | ------------------------------------------- |
| 1.21.1    | `versions/1.21.1/`   | `RestoreInventory-mc1.21.1-<version>.jar`   |
| 1.21.11   | `versions/1.21.11/`  | `RestoreInventory-mc1.21.11-<version>.jar`  |

Die Mod-Version (`mod_version` in `gradle.properties` ganz oben) ist
**fuer alle Subprojekte gleich**. Die Minecraft-/Fabric-Versionen sind
**pro Subprojekt** in `versions/<mc>/gradle.properties` festgelegt.

## Build

Multi-Version-Build mit einem Befehl:

```bash
./gradlew build           # Linux / macOS / Git Bash
.\gradlew.bat build       # Windows
```

JARs liegen am Ende sowohl in `build/libs/` (alle Versionen gesammelt)
als auch unter `versions/<mc>/build/libs/` (pro Version).

Nur eine Version bauen:

```bash
./gradlew :mc-1.21.1:build
./gradlew :mc-1.21.11:build
```

Voraussetzung: JDK 21. Gradle holt sich sonst per Toolchain ein passendes.

## Mod-Version erhoehen

```bash
python bump_version.py            # patch +1
python bump_version.py minor      # 2.2.0 -> 2.3.0
python bump_version.py major      # 2.2.0 -> 3.0.0
python bump_version.py 3.1.4      # explizit
python bump_version.py patch --tag      # zusaetzlich Git-Tag v<version>
python bump_version.py patch --commit   # Commit + Tag in einem Schritt
```

Anschliessend `git push --follow-tags`, dann baut die GitHub-Action und
haengt **alle JARs aller Versionen** ans Release.

## Fabric-Versionen aktualisieren

Holt aktuelle Yarn/Loader/Fabric-API-Versionen via fabric-meta + Modrinth:

```bash
python update_fabric.py            # alle Subprojekte aktualisieren
python update_fabric.py 1.21.11    # nur eine Version
python update_fabric.py --dry-run
```

## Eine neue Minecraft-Version hinzufuegen

1. Verzeichnis `versions/<mc>/` mit Standardstruktur anlegen
   (`src/main/java`, `src/main/resources/fabric.mod.json`, `gradle.properties`,
   `build.gradle`).
2. `python update_fabric.py <mc>` fuer die korrekten Yarn/Loader/API-Werte.
3. Code aus einer benachbarten Version kopieren und an die API-Brueche
   anpassen.

Das Build-System erkennt das neue Subprojekt automatisch — `settings.gradle`
inkludiert alle Verzeichnisse unter `versions/`.

## Manuelles Backup

```bash
./backup.sh
```

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
