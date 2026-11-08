# Usa una tua foto come sfondo di GlassHome (ridimensionata a 1080p).
#   .\scripts\wallpaper.ps1 <IP> C:\Users\me\Pictures\foto.jpg
param([Parameter(Mandatory = $true)][string]$Ip, [Parameter(Mandatory = $true)][string]$Photo)
. "$PSScriptRoot\lib.ps1"
if (-not (Test-Path $Photo)) { throw "Foto non trovata: $Photo" }
Set-Device $Ip
Connect-Tv

$tmp = Join-Path ([IO.Path]::GetTempPath()) 'glasshome-wallpaper.jpg'
Resize-Image $Photo $tmp 1920 1080 'jpeg'   # copre 1920x1080, mai ingrandita
$tvTmp = '/data/local/tmp/glasshome-wallpaper.jpg'
& $ADB -s $DEV push $tmp $tvTmp *> $null
Tv chmod 644 $tvTmp | Out-Null
Tv am start -W -n "$PKG/.MainActivity" --es from $tvTmp --es wallpaper custom | Out-Null
Start-Sleep -Seconds 2
Tv rm -f $tvTmp | Out-Null
Remove-Item $tmp -ErrorAction SilentlyContinue
Write-Host 'Sfondo impostato. Opzioni > Sfondo passa dalla tua foto a quelle incluse.'
