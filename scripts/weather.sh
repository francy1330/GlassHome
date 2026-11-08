#!/bin/sh
# Imposta la città del meteo mostrato nella barra in alto di GlassHome.
#   ./scripts/weather.sh <IP> "Milano"
#   ./scripts/weather.sh <IP> off          toglie il meteo
# La Fire TV cerca la città su Open-Meteo (gratuito, senza account) e salva solo le coordinate.
set -e
. "$(dirname "$0")/lib.sh"
set_device "$1"
[ -n "$2" ] || { echo "Uso: ./scripts/weather.sh <IP> \"Città\"   (oppure off)" >&2; exit 1; }
connect
q=$(printf '%s' "$2" | sed "s/'/'\\\\''/g") # apostrofi (es. L'Aquila) per la shell della TV
tv "am start -n $PKG/.MainActivity --es weather_city '$q'" >/dev/null
echo "Inviato. Dopo qualche secondo la TV mostra il risultato in alto (es. \"Meteo: Milano, Lombardia\")."
