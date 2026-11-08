#!/bin/sh
# Rimette una configurazione salvata con backup-config (anche su un'altra Fire TV).
#   ./scripts/restore-config.sh <IP>                      usa il backup più recente
#   ./scripts/restore-config.sh <IP> backups/config-...   usa quella cartella
set -e
. "$(dirname "$0")/lib.sh"
set_device "$1"
DIR=$2
[ -n "$DIR" ] || DIR=$(ls -d "$BACKUPS"/config-* 2>/dev/null | sort | tail -1)
[ -n "$DIR" ] && [ -f "$DIR/config.properties" ] || { echo "Nessun backup trovato (cartella con config.properties)." >&2; exit 1; }
connect
echo "Ripristino da $DIR"
TVTMP=/data/local/tmp/glasshome-config
tv rm -rf $TVTMP
"$ADB" -s "$DEV" push "$DIR" $TVTMP >/dev/null
tv chmod -R a+rX $TVTMP
tv am start -W -n "$PKG/.MainActivity" --es config import --es from $TVTMP >/dev/null 2>&1
sleep 2
tv rm -rf $TVTMP
tv input keyevent KEYCODE_HOME
echo "Fatto. Premi Home se la TV non si è già aggiornata."
