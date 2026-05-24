#!/usr/bin/env python3
"""
Aktualisiert Minecraft-, Yarn-, Fabric-Loader- und Fabric-API-Version
in gradle.properties auf den neuesten Stand.

Datenquellen (alle frei, ohne Account):
    - https://meta.fabricmc.net  (Yarn, Loader)
    - https://api.modrinth.com   (Fabric API)

Verwendung:
    python update_fabric.py                # nimmt neueste stabile MC-Version
    python update_fabric.py 1.21.11        # explizite MC-Version
    python update_fabric.py --dry-run      # zeigt nur was geaendert wuerde
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent
PROPS = ROOT / "gradle.properties"

KEYS = ("minecraft_version", "yarn_mappings", "loader_version", "fabric_api_version")


def http_json(url: str):
    req = urllib.request.Request(url, headers={"User-Agent": "RestoreInv-update-script"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


def latest_release_mc() -> str:
    data = http_json("https://meta.fabricmc.net/v2/versions/game")
    for entry in data:
        if entry.get("stable"):
            return entry["version"]
    raise SystemExit("FEHLER: Keine stabile Minecraft-Version von fabric-meta erhalten.")


def latest_yarn(mc: str) -> str:
    data = http_json(f"https://meta.fabricmc.net/v2/versions/yarn/{mc}")
    if not data:
        raise SystemExit(f"FEHLER: Keine Yarn-Mappings fuer Minecraft {mc} gefunden.")
    return data[0]["version"]


def latest_loader() -> str:
    data = http_json("https://meta.fabricmc.net/v2/versions/loader")
    for entry in data:
        if entry.get("stable"):
            return entry["version"]
    raise SystemExit("FEHLER: Keinen stabilen Fabric-Loader gefunden.")


def latest_fabric_api(mc: str) -> str:
    qs = urllib.parse.urlencode(
        {
            "game_versions": json.dumps([mc]),
            "loaders": json.dumps(["fabric"]),
        }
    )
    data = http_json(f"https://api.modrinth.com/v2/project/fabric-api/version?{qs}")
    if not data:
        raise SystemExit(f"FEHLER: Keine Fabric-API-Version fuer Minecraft {mc} gefunden.")
    # Modrinth liefert schon nach Datum sortiert - neueste zuerst.
    return data[0]["version_number"]


def read_props() -> dict[str, str]:
    text = PROPS.read_text(encoding="utf-8")
    out: dict[str, str] = {}
    for key in KEYS:
        m = re.search(rf"^{re.escape(key)}\s*=\s*(.+)\s*$", text, re.MULTILINE)
        if m:
            out[key] = m.group(1).strip()
    return out


def write_props(updates: dict[str, str]) -> None:
    text = PROPS.read_text(encoding="utf-8")
    for key, value in updates.items():
        pattern = rf"^{re.escape(key)}\s*=.*$"
        repl = f"{key}={value}"
        text, count = re.subn(pattern, repl, text, count=1, flags=re.MULTILINE)
        if count != 1:
            sys.exit(f"FEHLER: Konnte '{key}' nicht in gradle.properties ersetzen.")
    PROPS.write_text(text, encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("mc", nargs="?", help="Minecraft-Version (z.B. 1.21.11). Standard: neueste stabile.")
    parser.add_argument("--dry-run", action="store_true", help="Nur anzeigen, nicht schreiben.")
    args = parser.parse_args()

    mc = args.mc or latest_release_mc()
    print(f"Hole Versionen fuer Minecraft {mc} ...")

    new = {
        "minecraft_version": mc,
        "yarn_mappings":     latest_yarn(mc),
        "loader_version":    latest_loader(),
        "fabric_api_version": latest_fabric_api(mc),
    }

    current = read_props()

    print()
    print(f"{'Property':<22} {'Alt':<22} -> Neu")
    print("-" * 70)
    changed = False
    for key in KEYS:
        old = current.get(key, "<fehlt>")
        if old != new[key]:
            changed = True
            print(f"{key:<22} {old:<22} -> {new[key]}")
        else:
            print(f"{key:<22} {old:<22}    (unveraendert)")

    if not changed:
        print("\nAlles bereits aktuell.")
        return

    if args.dry_run:
        print("\n--dry-run: Keine Datei wurde geaendert.")
        return

    write_props(new)
    print(f"\ngradle.properties aktualisiert.")
    print("Build mit:  ./gradlew build")


if __name__ == "__main__":
    main()
