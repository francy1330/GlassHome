# Carica una tessera personalizzata (immagine 16:9, es. PNG 640x360) per un'app.
#   .\scripts\tile.ps1 <IP> list                        elenca i package delle app
#   .\scripts\tile.ps1 <IP> <package> immagine.png      es. com.netflix.ninja netflix.png
# Sulla TV: tieni premuto OK sull'app > Sistema icona > Ripristina originale per toglierla.
param([Parameter(Mandatory = $true)][string]$Ip, [string]$Package = 'list', [string]$Image)
. "$PSScriptRoot\lib.ps1"
Set-Device $Ip
Connect-Tv
if ($Package -eq 'list') {
    Write-Host 'App installate (package):'
    (Tv pm list packages -3) -split "`n" | ForEach-Object { '  ' + ($_ -replace '^package:', '') } | Sort-Object
    return
}
if (-not $Image -or -not (Test-Path $Image)) { throw 'Uso: .\scripts\tile.ps1 <IP> <package> immagine.png' }
$tmp = Join-Path ([IO.Path]::GetTempPath()) 'glasshome-tile.png'
Resize-Image $Image $tmp 640 360 'png'
$tvTmp = '/data/local/tmp/glasshome-tile.png'
& $ADB -s $DEV push $tmp $tvTmp *> $null
Tv chmod 644 $tvTmp | Out-Null
Tv am start -W -n "$PKG/.MainActivity" --es tile_pkg $Package --es from $tvTmp | Out-Null
Start-Sleep -Seconds 2
Tv rm -f $tvTmp | Out-Null
Remove-Item $tmp -ErrorAction SilentlyContinue
Write-Host "Tessera inviata per $Package."
