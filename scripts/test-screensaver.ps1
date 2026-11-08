# Verifica che GlassHome conviva con lo screensaver di sistema (Amazon Photos).
#   .\scripts\test-screensaver.ps1 <IP>
# Porta TEMPORANEAMENTE il timeout dello screensaver a 1 minuto e lo rimette com'era alla fine,
# anche in caso di errore o Ctrl+C. Dura circa 5 minuti: non toccare il telecomando.
# Non salva screenshot (lo screensaver mostra le tue foto): controlla tutto con dumpsys.
param([Parameter(Mandatory = $true)][string]$Ip)
. "$PSScriptRoot\lib.ps1"
Set-Device $Ip
Connect-Tv

$orig = Tv settings get system screen_off_timeout
if (-not $orig -or $orig -eq 'null') { $orig = Get-Original 'system:screen_off_timeout' }
if (-not $orig) { throw 'Non conosco il timeout originale: interrompo.' }

$script:pass = 0; $script:fail = 0
function Ok([string]$m) { Write-Host "  OK    $m"; $script:pass++ }
function Ko([string]$m) { Write-Host "  FAIL  $m" -ForegroundColor Red; $script:fail++ }
function Wakefulness { $m = [regex]::Match((Tv dumpsys power), 'mWakefulness=([A-Za-z]+)'); return $m.Groups[1].Value }
function Focus { $m = [regex]::Match((Tv dumpsys window), 'mCurrentFocus=Window\{\S+ \S+ ([^}]*)\}'); return $m.Groups[1].Value }
function Overlay {
    $txt = Tv dumpsys window windows
    $m = [regex]::Match($txt, "Window #\d+ Window\{\S+ u0 com\.glasshome\.launcher\}:[\s\S]*?mViewVisibility=(0x[0-9a-f]+)")
    return $m.Groups[1].Value
}
function OurWakelocks {
    $txt = Tv dumpsys power
    $m = [regex]::Match($txt, '(?ms)^Wake Locks:.*?(?=^\s*$)')
    return ($m.Value -split "`n" | Where-Object { $_ -match 'glasshome' }) -join ' '
}
function KeepScreenOn {
    $blocks = (Tv dumpsys window windows) -split '(?=Window #\d+ )'
    return @($blocks | Where-Object { $_ -match 'glasshome' -and $_ -match 'KEEP_SCREEN_ON' }).Count
}
function Wait-Dream { for ($i = 0; $i -lt 24; $i++) { if ((Wakefulness) -eq 'Dreaming') { return $i * 5 }; Start-Sleep 5 }; return -1 }
function Key([string]$k) { Tv input keyevent $k | Out-Null }
$since = Tv "date '+%m-%d %H:%M:%S.000'"

try {
    Show-Screensaver 'inizio test'
    Write-Host "screen_off_timeout originale: $orig ms  ->  60000 ms solo per il test"
    Tv settings put system screen_off_timeout 60000 | Out-Null

    Write-Host "`n1) Controlli statici"
    if (-not (OurWakelocks)) { Ok 'nessun wakelock di GlassHome' } else { Ko "wakelock: $(OurWakelocks)" }
    if ((KeepScreenOn) -eq 0) { Ok 'nessuna finestra GlassHome con FLAG_KEEP_SCREEN_ON' } else { Ko 'FLAG_KEEP_SCREEN_ON presente' }
    if ((Overlay) -eq '0x8') { Ok 'overlay del servizio nascosto a riposo' } else { Ko "overlay visibile a riposo ($(Overlay))" }

    Write-Host "`n2) Home -> GlassHome -> screensaver -> tasto -> GlassHome"
    Key KEYCODE_HOME; Start-Sleep 7
    if ((Focus) -match 'glasshome.*MainActivity') { Ok 'Home apre GlassHome' } else { Ko "dopo Home in primo piano: $(Focus)" }
    if ((Overlay) -eq '0x8') { Ok "overlay nascosto dopo l'arrivo dell'app" } else { Ko "overlay ancora visibile ($(Overlay))" }
    Write-Host '  ...attendo lo screensaver (non toccare il telecomando)'
    $t = Wait-Dream
    if ($t -ge 0) { Ok "screensaver partito sopra GlassHome (~${t}s)" } else { Ko 'lo screensaver non e'' partito in 120 s' }
    if ((Focus) -match 'dream') { Ok "in primo piano c'e' lo screensaver" } else { Ko "in primo piano: $(Focus)" }
    if (-not (OurWakelocks)) { Ok 'nessun wakelock di GlassHome durante lo screensaver' } else { Ko "wakelock: $(OurWakelocks)" }
    Key KEYCODE_BACK; Start-Sleep 3
    if ((Focus) -match 'glasshome.*MainActivity') { Ok 'un tasto chiude lo screensaver e torna a GlassHome' } else { Ko "dopo il tasto: $(Focus)" }

    Write-Host "`n3) Screensaver che parte con un MENU aperto sull'overlay"
    Tv "input keyevent KEYCODE_HOME; input keyevent KEYCODE_MENU" | Out-Null; Start-Sleep 7
    if ((Overlay) -eq '0x0') { Ok "menu aperto sull'overlay (overlay visibile)" }
    else { Ko "non riesco ad aprire il menu sull'overlay ($(Overlay)): test non significativo" }
    Write-Host '  ...attendo lo screensaver con il menu aperto'
    $t = Wait-Dream
    if ($t -ge 0) { Ok "screensaver partito anche con il menu aperto (~${t}s)" } else { Ko 'lo screensaver non e'' partito in 120 s' }
    Start-Sleep 2
    if ((Overlay) -eq '0x8') { Ok "overlay nascosto: lo screensaver e' visibile, non coperto" } else { Ko "OVERLAY SOPRA LO SCREENSAVER ($(Overlay))" }
    if ((Focus) -match 'dream') { Ok 'lo screensaver ha il focus' } else { Ko "in primo piano: $(Focus)" }
    Key KEYCODE_BACK; Start-Sleep 3
    if ((Focus) -match 'glasshome.*MainActivity') { Ok 'un tasto chiude lo screensaver e torna a GlassHome' } else { Ko "dopo il tasto: $(Focus)" }
    if ((Overlay) -eq '0x8') { Ok "overlay resta nascosto dopo lo screensaver" } else { Ko "overlay visibile dopo ($(Overlay))" }

    Write-Host "`n4) Screensaver con un menu aperto nell'app"
    Key KEYCODE_MENU; Start-Sleep 2
    $t = Wait-Dream
    if ($t -ge 0) { Ok "screensaver partito con il menu dell'app aperto (~${t}s)" } else { Ko 'lo screensaver non e'' partito in 120 s' }
    Key KEYCODE_BACK; Start-Sleep 3
    if ((Focus) -match 'glasshome.*MainActivity') { Ok 'ritorno a GlassHome' } else { Ko "dopo il tasto: $(Focus)" }
    Key KEYCODE_BACK

    Write-Host "`nLog di GlassHome durante il test:"
    (Tv logcat -d -s GlassHome -T "'$since'") -split "`n" | Where-Object { $_ -notmatch '^-----' } | Select-Object -Last 20 | ForEach-Object { "  $_" }
    Write-Host "`nRisultato: $($script:pass) OK, $($script:fail) FAIL"
} finally {
    Tv settings put system screen_off_timeout $orig | Out-Null
    Write-Host "`nscreen_off_timeout ripristinato: $(Tv settings get system screen_off_timeout)"
    Show-Screensaver 'fine test'
}
if ($script:fail -gt 0) { exit 1 }
