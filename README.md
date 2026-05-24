# Restore Inventory

Fabric-Mod fuer Minecraft, die den Spieler-Inventarstand sichert und
wiederherstellen kann.

| Aktuelle Version | Minecraft | Fabric Loader | Fabric API |
| ---------------- | --------- | ------------- | ---------- |
| siehe `gradle.properties` `mod_version` | 1.21.11 | 0.19.2 | 0.141.4+1.21.11 |

---

## Build

```bash
./gradlew build           # Linux / macOS / Git Bash
.\gradlew.bat build        # Windows CMD / PowerShell
```

Die fertige JAR landet in `build/libs/`.

Voraussetzungen: JDK 21 (Gradle holt sich sonst automatisch eines per Toolchain).

## Mod-Version erhoehen

Alle Versionen liegen ausschliesslich in `gradle.properties`. Erhoehen mit:

```bash
python bump_version.py            # patch +1   (Standard)
python bump_version.py minor      # 2.0.5 -> 2.1.0
python bump_version.py major      # 2.0.5 -> 3.0.0
python bump_version.py 3.1.4      # explizit setzen
python bump_version.py patch --tag     # zusaetzlich Git-Tag v<version>
python bump_version.py patch --commit  # Commit + Tag in einem Schritt
```

Anschliessend `git push --follow-tags` -- die GitHub-Action baut dann
automatisch ein Release mit angehaengter JAR.

## Auf neuere Minecraft-Version anheben

Holt aktuelle Versionen via Fabric-Meta- und Modrinth-API:

```bash
python update_fabric.py            # neueste stabile MC-Version
python update_fabric.py 1.21.11    # explizit
python update_fabric.py --dry-run  # nur anzeigen
```

## Builds fuer mehrere Minecraft-Versionen

```bash
./multi-build.sh                                  # Defaults
./multi-build.sh 1.21.7 1.21.11
.\multi-build.ps1 -Versions '1.21.7','1.21.11'   # Windows
```

JARs landen in `builds/<mc>/`.

## Manuelles Backup

```bash
./backup.sh
```

Erstellt einen Commit mit Zeitstempel und pusht ihn. Funktioniert in jedem
geklonten Repo, der Pfad wird automatisch erkannt.

## Lokales JDK setzen (optional)

Wenn Gradle dein JDK nicht findet, lege `gradle-local.properties` an:

```properties
org.gradle.java.home=C:\\Program Files\\Java\\jdk-21
```

Die Datei ist via `.gitignore` ausgeschlossen.

## Release-Workflow auf einen Blick

1. `python bump_version.py minor --commit`
2. `git push --follow-tags`
3. GitHub-Action baut und legt Release samt JAR an.
