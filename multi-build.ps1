#requires -Version 5.1
<#
.SYNOPSIS
    Baut die Mod fuer mehrere Minecraft-Versionen.
.DESCRIPTION
    Aktualisiert per update_fabric.py die Versionen in gradle.properties,
    baut die Mod, kopiert die JAR nach builds/<mc>/ und stellt am Ende
    die urspruengliche gradle.properties wieder her.
.EXAMPLE
    .\multi-build.ps1
    .\multi-build.ps1 -Versions '1.21.7','1.21.11'
#>

param(
    [string[]]$Versions = @('1.21.4','1.21.6','1.21.7','1.21.11')
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
Set-Location $root

$backup = "$root\gradle.properties.bak"
Copy-Item "$root\gradle.properties" $backup -Force

try {
    foreach ($v in $Versions) {
        Write-Host ""
        Write-Host "==> Baue fuer Minecraft $v" -ForegroundColor Cyan

        python update_fabric.py $v
        if ($LASTEXITCODE -ne 0) { throw "update_fabric.py fehlgeschlagen fuer $v" }

        & "$root\gradlew.bat" build --no-daemon
        if ($LASTEXITCODE -ne 0) { throw "gradle build fehlgeschlagen fuer $v" }

        $target = "$root\builds\$v"
        New-Item -ItemType Directory -Force -Path $target | Out-Null
        Copy-Item "$root\build\libs\*.jar" $target -Force
        Write-Host "    JARs in $target" -ForegroundColor Green
    }
}
finally {
    Move-Item $backup "$root\gradle.properties" -Force
    Write-Host ""
    Write-Host "gradle.properties wurde wiederhergestellt." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Fertig. Builds liegen unter builds/<version>/" -ForegroundColor Green
