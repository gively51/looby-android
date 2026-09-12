#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Nettoyage complet des caches Gradle/KSP/Kotlin pour le projet Android Looby.
    A executer apres un crash memoire (GC thrashing) qui corrompt souvent le
    cache incremental KSP et casse la generation Hilt (@AndroidEntryPoint /
    @HiltAndroidApp "Expected ... to have a value").

.EXAMPLE
    .\clean-android-caches.ps1
#>

$ProjectDir = "C:\Users\Shadow\source\repos\loobyweb\mobile\android"

Write-Host "▸ Arrêt des daemons Gradle/Kotlin..." -ForegroundColor Cyan
Set-Location $ProjectDir
if (Test-Path ".\gradlew.bat") {
    & .\gradlew.bat --stop
}

Write-Host "▸ Suppression des dossiers de build et caches incrémentaux..." -ForegroundColor Cyan
$paths = @(
    "$ProjectDir\build",
    "$ProjectDir\app\build",
    "$ProjectDir\.gradle",
    "$ProjectDir\.kotlin"
)
foreach ($p in $paths) {
    if (Test-Path $p) {
        Write-Host "  - Suppression : $p" -ForegroundColor Yellow
        Remove-Item -Recurse -Force $p -ErrorAction SilentlyContinue
    }
}

# Cache global Gradle KSP/Kotlin (utilisateur) - ne supprime que les caches de transformation, pas les jars téléchargés
$userGradleCaches = "$env:USERPROFILE\.gradle\caches"
if (Test-Path $userGradleCaches) {
    Write-Host "▸ Nettoyage du cache KSP global utilisateur (transforms uniquement)..." -ForegroundColor Cyan
    Get-ChildItem -Path $userGradleCaches -Directory -Filter "transforms-*" -ErrorAction SilentlyContinue |
        ForEach-Object { Remove-Item -Recurse -Force $_.FullName -ErrorAction SilentlyContinue }
}

Write-Host "▸ Terminé. Relancez le build avec :" -ForegroundColor Green
Write-Host "    cd mobile/android" -ForegroundColor White
Write-Host "    ./gradlew clean :app:assembleDebug --no-build-cache" -ForegroundColor White
