#!/bin/sh
# Usa una tua foto come sfondo di GlassHome (ridimensionata a 1080p).
#   ./scripts/wallpaper.sh <IP> ~/Immagini/foto.jpg
set -e
. "$(dirname "$0")/lib.sh"
set_device "$1"
[ -f "$2" ] || { echo "Uso: ./scripts/wallpaper.sh <IP> foto.jpg" >&2; exit 1; }
connect

tmpdir=$(mktemp -d); tmp=$tmpdir/wallpaper.jpg
cp "$2" "$tmpdir/src"
if command -v sips >/dev/null; then
  # Copre 1920x1080: il lato "corto" rispetto al 16:9 diventa 1080 o 1920.
  w=$(sips -g pixelWidth "$tmpdir/src" | awk '/pixelWidth/{print $2}')
  h=$(sips -g pixelHeight "$tmpdir/src" | awk '/pixelHeight/{print $2}')
  if [ $((w * 9)) -gt $((h * 16)) ]; then
    [ "$h" -gt 1080 ] && sips --resampleHeight 1080 "$tmpdir/src" >/dev/null
  else
    [ "$w" -gt 1920 ] && sips --resampleWidth 1920 "$tmpdir/src" >/dev/null
  fi
  sips -s format jpeg -s formatOptions 88 "$tmpdir/src" --out "$tmp" >/dev/null
elif command -v magick >/dev/null; then
  magick "$tmpdir/src" -resize '1920x1080^>' -quality 88 "$tmp"
else
  cp "$tmpdir/src" "$tmp"
fi

TVTMP=/data/local/tmp/glasshome-wallpaper.jpg
"$ADB" -s "$DEV" push "$tmp" $TVTMP >/dev/null
tv chmod 644 $TVTMP
tv am start -W -n "$PKG/.MainActivity" --es from $TVTMP --es wallpaper custom >/dev/null 2>&1
sleep 2
tv rm -f $TVTMP
rm -rf "$tmpdir"
echo "Sfondo impostato. Opzioni > Sfondo passa dalla tua foto a quelle incluse."
