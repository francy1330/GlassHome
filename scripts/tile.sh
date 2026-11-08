#!/bin/sh
# Carica una tessera personalizzata (immagine 16:9, es. PNG 640x360) per un'app.
#   ./scripts/tile.sh <IP> list                         elenca i package delle app
#   ./scripts/tile.sh <IP> <package> immagine.png       es. com.netflix.ninja netflix.png
# Sulla TV: tieni premuto OK sull'app > Sistema icona > Ripristina originale per toglierla.
set -e
. "$(dirname "$0")/lib.sh"
set_device "$1"
connect
if [ "$2" = list ] || [ -z "$2" ]; then
  echo "App installate (package):"
  tv pm list packages -3 | sed 's/^package:/  /' | sort
  exit 0
fi
[ -f "$3" ] || { echo "Uso: ./scripts/tile.sh <IP> <package> immagine.png" >&2; exit 1; }
tmpdir=$(mktemp -d); tmp=$tmpdir/tile.png
if command -v sips >/dev/null; then
  sips -s format png --resampleWidth 640 "$3" --out "$tmp" >/dev/null
else
  cp "$3" "$tmp"
fi
TVTMP=/data/local/tmp/glasshome-tile.png
"$ADB" -s "$DEV" push "$tmp" $TVTMP >/dev/null
tv chmod 644 $TVTMP
tv am start -W -n "$PKG/.MainActivity" --es tile_pkg "$2" --es from $TVTMP >/dev/null 2>&1
sleep 2
tv rm -f $TVTMP
rm -rf "$tmpdir"
echo "Tessera inviata per $2."
