# Funzioni comuni agli script PowerShell di GlassHome (Windows). Non si esegue da solo.
# Stesso comportamento di lib.sh.

$ErrorActionPreference = 'Stop'
$PKG = 'com.glasshome.launcher'
$SVC = "$PKG/$PKG.HomeService"
$ROOT = Split-Path -Parent $PSScriptRoot
$BACKUPS = Join-Path $ROOT 'backups'
$TV_BACKUP = '/data/local/tmp/glasshome-backup.properties'
$KEYS = @('secure:enabled_accessibility_services', 'secure:accessibility_enabled',
          'secure:screensaver_components', 'system:screen_off_timeout')

function Find-Adb {
    $candidates = @(
        (Join-Path $ROOT 'toolchain\sdk\platform-tools\adb.exe'),
        (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe')
    )
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { $candidates = @($cmd.Source) + $candidates }
    foreach ($c in $candidates) { if ($c -and (Test-Path $c)) { return $c } }
    throw "adb non trovato. Vedi README.md, sezione 'Cosa serve'."
}
$ADB = Find-Adb

function Set-Device([string]$ip) {
    if (-not $ip) { throw "Manca l'indirizzo IP della Fire TV (Impostazioni > La mia Fire TV > Informazioni > Rete)." }
    if ($ip -notmatch ':') { $ip = "${ip}:5555" }
    $script:DEV = $ip
    $script:SAFE = ($ip -replace '[:.]', '_')
}

# Esegue un comando nella shell della TV e restituisce l'output come testo (senza \r).
function Tv {
    $out = & $ADB -s $script:DEV shell @args 2>$null
    if ($null -eq $out) { return '' }
    return (($out | Out-String) -replace "`r", '').TrimEnd("`n")
}

function Connect-Tv {
    & $ADB connect $script:DEV *> $null
    $state = (& $ADB -s $script:DEV get-state 2>$null)
    if ($state -ne 'device') {
        Write-Host 'Guarda la TV: scegli "Consenti sempre da questo computer" e premi OK.'
        for ($i = 0; $i -lt 45 -and $state -ne 'device'; $i++) {
            Start-Sleep -Seconds 2
            & $ADB connect $script:DEV *> $null
            $state = (& $ADB -s $script:DEV get-state 2>$null)
        }
    }
    if ($state -ne 'device') { throw "Non riesco a collegarmi a $($script:DEV). Il Debug ADB e' attivo? (Impostazioni > La mia Fire TV > Opzioni sviluppatore)" }
    Write-Host "Collegato a $(Tv getprop ro.product.model), $(Tv getprop ro.build.version.name)"
}

function Get-Setting([string]$key) {
    $ns, $name = $key -split ':', 2
    return (Tv settings get $ns $name)
}

# Salva le impostazioni attuali. Il PRIMO backup (original-*) non viene mai sovrascritto.
function Backup-Settings {
    New-Item -ItemType Directory -Force -Path $BACKUPS | Out-Null
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $script:SNAP = Join-Path $BACKUPS "$($script:SAFE)-$stamp.properties"
    $lines = @("# GlassHome: impostazioni di $($script:DEV) il $(Get-Date)")
    foreach ($k in $KEYS) { $lines += "$k=$(Get-Setting $k)" }
    [IO.File]::WriteAllLines($script:SNAP, $lines)
    $orig = Join-Path $BACKUPS "original-$($script:SAFE).properties"
    if (-not (Test-Path $orig)) {
        $onTv = Tv "[ -f $TV_BACKUP ] && cat $TV_BACKUP"
        if ($onTv) {
            [IO.File]::WriteAllText($orig, $onTv + "`n")
            Write-Host "Backup originale recuperato dalla TV: $orig"
        } else {
            Copy-Item $script:SNAP $orig
            & $ADB -s $script:DEV push $orig $TV_BACKUP *> $null
            Write-Host "Backup originale salvato: $orig (e sulla TV in $TV_BACKUP)"
        }
    }
    Write-Host "Istantanea: $($script:SNAP)"
}

# Valore salvato nel backup originale ('' se assente).
function Get-Original([string]$key) {
    $orig = Join-Path $BACKUPS "original-$($script:SAFE).properties"
    if (-not (Test-Path $orig)) {
        $onTv = Tv "[ -f $TV_BACKUP ] && cat $TV_BACKUP"
        if ($onTv) { New-Item -ItemType Directory -Force -Path $BACKUPS | Out-Null; [IO.File]::WriteAllText($orig, $onTv + "`n") }
    }
    if (-not (Test-Path $orig)) { return '' }
    foreach ($l in Get-Content $orig) { if ($l.StartsWith("$key=")) { return $l.Substring($key.Length + 1) } }
    return ''
}

function Show-Screensaver([string]$when) {
    Write-Host "screensaver_components ($when): $(Tv settings get secure screensaver_components)"
}

# Ridimensiona un'immagine con System.Drawing (incluso in Windows PowerShell).
function Resize-Image([string]$src, [string]$dst, [int]$coverW, [int]$coverH, [string]$format) {
    Add-Type -AssemblyName System.Drawing
    $img = [System.Drawing.Image]::FromFile((Resolve-Path $src))
    try {
        $k = [Math]::Max($coverW / $img.Width, $coverH / $img.Height)
        if ($k -gt 1) { $k = 1 }  # mai ingrandire
        $w = [int][Math]::Round($img.Width * $k); $h = [int][Math]::Round($img.Height * $k)
        $bmp = New-Object System.Drawing.Bitmap $w, $h
        $g = [System.Drawing.Graphics]::FromImage($bmp)
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.DrawImage($img, 0, 0, $w, $h)
        $g.Dispose()
        if ($format -eq 'png') { $bmp.Save($dst, [System.Drawing.Imaging.ImageFormat]::Png) }
        else {
            $codec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object { $_.MimeType -eq 'image/jpeg' }
            $p = New-Object System.Drawing.Imaging.EncoderParameters 1
            $p.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter ([System.Drawing.Imaging.Encoder]::Quality), 88L
            $bmp.Save($dst, $codec, $p)
        }
        $bmp.Dispose()
    } finally { $img.Dispose() }
}
