#!/bin/sh
# Salva sul computer la configurazione di GlassHome: dock, app nascoste, tessere personalizzate,
# sfondo, opzioni e città del meteo.
#   ./scripts/backup-config.sh <IP>        → backups/config-AAAAMMGG-HHMMSS/
set -e
. "$(dirname "$0")/lib.sh"
set_device "$1"
connect
tv am start -W -n "$PKG/.MainActivity" --es config export >/dev/null 2>&1
sleep 2
SRC=/sdcard/Android/data/$PKG/files/backup
if ! tv "ls $SRC/config.properties" | grep -q config.properties; then
  echo "La TV non ha preparato il backup. GlassHome è installata e aggiornata?" >&2; exit 1
fi
OUT=$BACKUPS/config-$(date +%Y%m%d-%H%M%S)
mkdir -p "$OUT"
"$ADB" -s "$DEV" pull "$SRC/." "$OUT" >/dev/null
echo "Configurazione salvata in: $OUT"
echo "  $(grep -c '=' "$OUT/config.properties") impostazioni, $(ls "$OUT/tiles" 2>/dev/null | wc -l | tr -d ' ') tessere personalizzate$( [ -f "$OUT/wallpaper.jpg" ] && echo ', la tua foto di sfondo')"
echo "Per rimetterla: ./scripts/restore-config.sh <IP> \"$OUT\""
