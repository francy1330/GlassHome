# Imposta la città del meteo mostrato nella barra in alto di GlassHome.
#   .\scripts\weather.ps1 <IP> "Milano"
#   .\scripts\weather.ps1 <IP> off          toglie il meteo
# La Fire TV cerca la città su Open-Meteo (gratuito, senza account) e salva solo le coordinate.
param([Parameter(Mandatory = $true)][string]$Ip, [Parameter(Mandatory = $true)][string]$City)
. "$PSScriptRoot\lib.ps1"
Set-Device $Ip
Connect-Tv
$q = $City -replace "'", "'\''"   # apostrofi (es. L'Aquila) per la shell della TV
Tv "am start -n $PKG/.MainActivity --es weather_city '$q'" | Out-Null
Write-Host 'Inviato. Dopo qualche secondo la TV mostra il risultato in alto (es. "Meteo: Milano, Lombardia").'
