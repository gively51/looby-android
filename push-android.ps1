#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Script de push pour le projet Android Looby vers GitHub

.DESCRIPTION
    Navigue vers mobile/android, commit les changements et pousse vers gively51/looby-android

.EXAMPLE
    .\push-android.ps1
    # Ou avec un message personnalisé :
    .\push-android.ps1 -Message "fix: gradle memory settings for macOS"
#>
param(
    [string]$Message = "chore: update gradle.properties for optimal macOS build"
)

# Couleurs pour les messages
$Color = @{
    Success = "Green"
    Error   = "Red"
    Info    = "Cyan"
    Warning = "Yellow"
}

function Write-Status {
    param([string]$Text, [string]$Color = "White")
    Write-Host "▸ $Text" -ForegroundColor $Color
}

function Write-Title {
    param([string]$Text)
    Write-Host ""
    Write-Host "════════════════════════════════════════" -ForegroundColor Cyan
    Write-Host "  $Text" -ForegroundColor Cyan
    Write-Host "════════════════════════════════════════" -ForegroundColor Cyan
}

# Configuration
$GitExe = "C:\Program Files\Microsoft Visual Studio\18\Enterprise\Common7\IDE\CommonExtensions\Microsoft\TeamFoundation\Team Explorer\Git\cmd\git.exe"
$ProjectDir = "C:\Users\Shadow\source\repos\loobyweb\mobile\android"
$Branch = "master"

Write-Title "Push Android Looby → GitHub"

# Vérifier que git existe
if (-not (Test-Path $GitExe)) {
    Write-Status "Erreur : git.exe non trouvé" $Color.Error
    Write-Status "Cherche git.exe via winget..." $Color.Warning
    # Fallback : essayer git via PATH ou GitHub CLI
    if (Get-Command git -ErrorAction SilentlyContinue) {
        $GitExe = "git"
        Write-Status "git.exe trouvé via PATH" $Color.Success
    } else {
        Write-Status "Impossible de trouver git" $Color.Error
        exit 1
    }
}

# Naviguer vers le répertoire du projet
Write-Status "Navigation vers $ProjectDir"
Set-Location $ProjectDir
if ($LASTEXITCODE -ne 0) {
    Write-Status "Impossible d'accéder au répertoire" $Color.Error
    exit 1
}
Write-Status "Répertoire courant : $(Get-Location)" $Color.Success

# Vérifier le statut git
Write-Status "Vérification du statut git..." $Color.Info
& $GitExe status --short
$Changes = & $GitExe status --porcelain
if (-not $Changes) {
    Write-Status "Aucun changement à commiter" $Color.Warning
    exit 0
}

# Afficher les fichiers modifiés
Write-Title "Changements détectés"
& $GitExe status

# Ajouter tous les changements
Write-Status "Ajout des changements..."
& $GitExe add .
if ($LASTEXITCODE -ne 0) {
    Write-Status "Erreur lors de 'git add'" $Color.Error
    exit 1
}
Write-Status "Changements ajoutés" $Color.Success

# Commit
Write-Title "Commit"
Write-Status "Message : '$Message'"
& $GitExe commit -m $Message
if ($LASTEXITCODE -ne 0) {
    Write-Status "Erreur lors du commit" $Color.Error
    exit 1
}
Write-Status "Commit effectué" $Color.Success

# Vérifier la branche et le remote
Write-Title "Vérification du remote"
$CurrentBranch = & $GitExe rev-parse --abbrev-ref HEAD
Write-Status "Branche courante : $CurrentBranch"

$Remote = & $GitExe config --get remote.github.url
if (-not $Remote) {
    Write-Status "Remote 'github' non trouvé, création..." $Color.Warning
    & $GitExe remote add github https://github.com/gively51/looby-android.git
    Write-Status "Remote 'github' ajouté" $Color.Success
} else {
    Write-Status "Remote 'github' : $Remote" $Color.Success
}

# Push
Write-Title "Push vers GitHub"
Write-Status "Push de '$CurrentBranch' vers 'github/$CurrentBranch'..."
& $GitExe push -u github $CurrentBranch
if ($LASTEXITCODE -ne 0) {
    Write-Status "Erreur lors du push" $Color.Error
    exit 1
}
Write-Status "Push réussi !" $Color.Success

# Afficher le résumé
Write-Title "Résumé"
Write-Status "Dépôt : https://github.com/gively51/looby-android" $Color.Success
Write-Status "Branche : $CurrentBranch" $Color.Success
& $GitExe log --oneline -1
Write-Host ""
Write-Status "Poussé avec succès" $Color.Success
