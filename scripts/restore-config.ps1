# Rimette una configurazione salvata con backup-config (anche su un'altra Fire TV).
#   .\scripts\restore-config.ps1 <IP>                       usa il backup piu' recente
#   .\scripts\restore-config.ps1 <IP> backups\config-...    usa quella cartella
param([Parameter(Mandatory = $true)][string]$Ip, [string]$Dir)
. "$PSScriptRoot\lib.ps1"
Set-Device $Ip
if (-not $Dir) {
    $last = Get-ChildItem $BACKUPS -Directory -Filter 'config-*' -ErrorAction SilentlyContinue | Sort-Object Name | Select-Object -Last 1
    if ($last) { $Dir = $last.FullName }
}
if (-not $Dir -or -not (Test-Path (Join-Path $Dir 'config.properties'))) { throw 'Nessun backup trovato (cartella con config.properties).' }
Connect-Tv
Write-Host "Ripristino da $Dir"
$tvTmp = '/data/local/tmp/glasshome-config'
Tv rm -rf $tvTmp | Out-Null
& $ADB -s $DEV push $Dir $tvTmp *> $null
Tv chmod -R a+rX $tvTmp | Out-Null
Tv am start -W -n "$PKG/.MainActivity" --es config import --es from $tvTmp | Out-Null
Start-Sleep -Seconds 2
Tv rm -rf $tvTmp | Out-Null
Tv input keyevent KEYCODE_HOME | Out-Null
Write-Host 'Fatto. Premi Home se la TV non si e'' gia'' aggiornata.'
