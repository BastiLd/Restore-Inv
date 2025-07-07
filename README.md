# Restore-Inv

Dieses Projekt enthält ein Minecraft-Mod und ein Backup-Skript für automatische und manuelle Sicherungen auf GitHub.

## Manuelles Backup auf GitHub

Mit dem Skript `backup.sh` kannst du jederzeit ein Backup deines aktuellen Projektstandes auf GitHub machen. Jeder Commit erhält einen Zeitstempel, sodass keine Backups überschrieben werden.

### Verwendung

1. **Öffne Git Bash** im Projektordner:
   ```bash
   cd "/c/Users/basti/Documents/INV MOD"
   ./backup.sh
   ```
2. Das Skript erstellt einen Commit mit Zeitstempel und pusht ihn zu GitHub.

### Voraussetzungen

- Git muss installiert sein
- GitHub-Repository muss eingerichtet und verbunden sein
- Das Skript funktioniert am besten in Git Bash

## Wo finde ich die Backups auf GitHub?

- **Jedes Backup ist ein Commit** in deinem Repository.
- Gehe auf [dein GitHub-Repository](https://github.com/BastiLd/Restore-Inv)
- Klicke oben auf "Commits" (meistens neben dem Branch-Namen `main`)
- Dort siehst du alle Backups mit Zeitstempel im Commit-Namen

## Hinweise

- Die Warnung zu "LF will be replaced by CRLF" ist unkritisch und kann ignoriert werden.
- Das Skript sichert alle Änderungen, die noch nicht committet wurden.

---

**Viel Spaß beim Sichern und Entwickeln!**
