# Installa GlassHome sulla Fire TV e attiva il servizio del tasto Home.
#   .\scripts\install.ps1 <IP-della-Fire-TV>
# Non disattiva ne' nasconde il launcher Amazon e non tocca lo screensaver.
param([Parameter(Mandatory = $true)][string]$Ip)
. "$PSScriptRoot\lib.ps1"
Set-Device $Ip
Connect-Tv

Show-Screensaver 'prima'
Backup-Settings

$manifest = Get-Content (Join-Path $ROOT 'app\AndroidManifest.xml') -Raw
$version = [regex]::Match($manifest, 'versionName="([^"]+)"').Groups[1].Value
$apk = Join-Path $ROOT "dist\GlassHome-$version.apk"
if (-not (Test-Path $apk)) { & (Join-Path $ROOT 'build.ps1') }
Write-Host "Installo $apk ..."
& $ADB -s $DEV install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'Installazione non riuscita.' }

# Aggiunge il nostro servizio a quelli gia' attivi (separati da ':'), senza toglierne nessuno.
$cur = Tv settings get secure enabled_accessibility_services
if ($cur -like "*$SVC*") { Write-Host 'Servizio Home gia'' attivo.' }
elseif (-not $cur -or $cur -eq 'null') { Tv settings put secure enabled_accessibility_services $SVC | Out-Null }
else { Tv settings put secure enabled_accessibility_services "${cur}:$SVC" | Out-Null }
Tv settings put secure accessibility_enabled 1 | Out-Null
Write-Host "enabled_accessibility_services: $(Tv settings get secure enabled_accessibility_services)"

Tv am start -n "$PKG/.MainActivity" | Out-Null
Show-Screensaver 'dopo'
$before = (Get-Content $SNAP | Where-Object { $_ -like 'secure:screensaver_components=*' }) -replace '^secure:screensaver_components=', ''
if ($before -eq (Tv settings get secure screensaver_components)) { Write-Host 'Screensaver invariato: OK.' }
else { Write-Warning 'screensaver_components e'' cambiato! Esegui restore-defaults e segnalalo.' }
Write-Host 'Fatto. Premi Home sul telecomando: deve comparire GlassHome.'
