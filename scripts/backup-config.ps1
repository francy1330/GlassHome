# Salva sul computer la configurazione di GlassHome: dock, app nascoste, tessere personalizzate,
# sfondo, opzioni e città del meteo.
#   .\scripts\backup-config.ps1 <IP>        -> backups\config-AAAAMMGG-HHMMSS\
param([Parameter(Mandatory = $true)][string]$Ip)
. "$PSScriptRoot\lib.ps1"
Set-Device $Ip
Connect-Tv
Tv am start -W -n "$PKG/.MainActivity" --es config export | Out-Null
Start-Sleep -Seconds 2
$src = "/sdcard/Android/data/$PKG/files/backup"
if ((Tv "ls $src/config.properties") -notmatch 'config.properties') {
    throw 'La TV non ha preparato il backup. GlassHome e'' installata e aggiornata?'
}
$out = Join-Path $BACKUPS ("config-" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Force -Path $out | Out-Null
& $ADB -s $DEV pull "$src/." $out *> $null
$n = (Get-Content (Join-Path $out 'config.properties') | Where-Object { $_ -match '=' }).Count
$t = @(Get-ChildItem (Join-Path $out 'tiles') -ErrorAction SilentlyContinue).Count
Write-Host "Configurazione salvata in: $out"
Write-Host "  $n impostazioni, $t tessere personalizzate"
Write-Host "Per rimetterla: .\scripts\restore-config.ps1 <IP> `"$out`""
