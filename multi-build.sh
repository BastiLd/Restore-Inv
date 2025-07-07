#!/bin/bash

# Liste der gewünschten Minecraft-Versionen
versions=("1.21.2" "1.21.3" "1.21.4" "1.21.5" "1.21.6" "1.21.7")

# Backup der originalen build.gradle
cp build.gradle build.gradle.bak

for v in "${versions[@]}"
do
    echo "Baue für Minecraft $v ..."
    # Passe die Version in build.gradle an (ggf. Zeile anpassen, falls anders bei dir!)
    sed -i "s/minecraft_version = \".*\"/minecraft_version = \"$v\"/g" build.gradle

    # Baue die Mod mit gradlew.bat (Windows)
    ./gradlew.bat build

    # Lege die gebaute JAR in einen eigenen Ordner ab
    mkdir -p builds/$v
    cp build/libs/*.jar builds/$v/
done

# Stelle die originale build.gradle wieder her
mv build.gradle.bak build.gradle

echo "Fertig! Alle Builds liegen in den jeweiligen builds/<version>-Ordnern."