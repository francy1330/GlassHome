# Torna come prima di GlassHome.
#   .\scripts\restore-defaults.ps1 <IP>              toglie SOLO il servizio Home di GlassHome e
#                                                    ripristina i valori salvati
#   .\scripts\restore-defaults.ps1 <IP> -Uninstall   ...e disinstalla SOLO l'app GlassHome
# Lo screensaver non viene mai modificato: viene solo stampato per controllo.
param([Parameter(Mandatory = $true)][string]$Ip, [switch]$Uninstall)
. "$PSScriptRoot\lib.ps1"
Set-Device $Ip
Connect-Tv
Show-Screensaver 'prima'

# 1. Via il nostro servizio, lasciando gli altri come sono.
$cur = Tv settings get secure enabled_accessibility_services
$rest = (($cur -split ':') | Where-Object { $_ -and $_ -ne $SVC -and $_ -ne 'null' }) -join ':'
if ($rest) {
    Tv settings put secure enabled_accessibility_services $rest | Out-Null
} else {
    $o = Get-Original 'secure:enabled_accessibility_services'
    if ($o -and $o -ne 'null' -and $o -ne $SVC) { Tv settings put secure enabled_accessibility_services $o | Out-Null }
    else { Tv settings delete secure enabled_accessibility_services | Out-Null }
    $o = Get-Original 'secure:accessibility_enabled'
    if ($o -and $o -ne 'null') { Tv settings put secure accessibility_enabled $o | Out-Null }
}
Write-Host "enabled_accessibility_services: $(Tv settings get secure enabled_accessibility_services)"
Write-Host "accessibility_enabled: $(Tv settings get secure accessibility_enabled)"

# 2. Timeout dello screensaver (cambia solo durante i test): torna al valore salvato.
$o = Get-Original 'system:screen_off_timeout'
if ($o -and $o -ne 'null' -and (Tv settings get system screen_off_timeout) -ne $o) {
    Tv settings put system screen_off_timeout $o | Out-Null
}
Write-Host "screen_off_timeout: $(Tv settings get system screen_off_timeout)"

# 3. Lo screensaver: solo controllo.
$o = Get-Original 'secure:screensaver_components'
Show-Screensaver 'dopo'
if (-not $o -or $o -eq (Tv settings get secure screensaver_components)) { Write-Host 'Screensaver invariato: OK.' }
else { Write-Host "Nota: lo screensaver ora e' diverso da quello salvato ($o). GlassHome non lo modifica: controlla in Impostazioni > Display." }

if ($Uninstall) {
    & $ADB -s $DEV uninstall $PKG
    if ($LASTEXITCODE -eq 0) { Write-Host 'GlassHome disinstallata.' }
    Tv rm -f $TV_BACKUP | Out-Null
}
Tv input keyevent KEYCODE_HOME | Out-Null
Write-Host 'Fatto: il tasto Home apre di nuovo la home Amazon. Ricordati di spegnere il Debug ADB quando hai finito.'
