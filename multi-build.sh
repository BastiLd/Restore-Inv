#!/usr/bin/env bash
# Baut die Mod fuer mehrere Minecraft-Versionen.
# Versionen werden per update_fabric.py gesetzt, am Ende wird die
# urspruengliche gradle.properties wiederhergestellt.

set -euo pipefail

cd "$(dirname "$0")"

versions=("${@:-1.21.4 1.21.6 1.21.7 1.21.11}")

cp gradle.properties gradle.properties.bak
trap 'mv gradle.properties.bak gradle.properties' EXIT

for v in "${versions[@]}"; do
  echo
  echo "==> Baue fuer Minecraft $v"

  python update_fabric.py "$v"
  ./gradlew build --no-daemon

  mkdir -p "builds/$v"
  cp build/libs/*.jar "builds/$v/"
  echo "    JARs in builds/$v"
done

echo
echo "Fertig. Builds liegen unter builds/<version>/"
