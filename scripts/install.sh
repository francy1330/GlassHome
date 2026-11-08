#!/bin/sh
# Installa GlassHome sulla Fire TV e attiva il servizio del tasto Home.
#   ./scripts/install.sh <IP-della-Fire-TV>
# Non disattiva né nasconde il launcher Amazon e non tocca lo screensaver.
set -e
. "$(dirname "$0")/lib.sh"
set_device "$1"
connect

show_screensaver "prima"
backup_settings

VERSION=$(sed -n 's/.*versionName="\([^"]*\)".*/\1/p' "$ROOT/app/AndroidManifest.xml")
APK=$ROOT/dist/GlassHome-$VERSION.apk
[ -f "$APK" ] || "$ROOT/build.sh"
echo "Installo $APK ..."
"$ADB" -s "$DEV" install -r "$APK"

# Aggiunge il nostro servizio a quelli già attivi (separati da ':'), senza toglierne nessuno.
cur=$(tv settings get secure enabled_accessibility_services)
case "$cur" in
  *"$SVC"*) echo "Servizio Home già attivo." ;;
  ""|null) tv settings put secure enabled_accessibility_services "$SVC" ;;
  *) tv settings put secure enabled_accessibility_services "$cur:$SVC" ;;
esac
tv settings put secure accessibility_enabled 1
echo "enabled_accessibility_services: $(tv settings get secure enabled_accessibility_services)"

tv am start -n "$PKG/.MainActivity" >/dev/null 2>&1 || true
show_screensaver "dopo"
before=$(sed -n 's/^secure:screensaver_components=//p' "$snap")
[ "$before" = "$(tv settings get secure screensaver_components)" ] && echo "Screensaver invariato: OK." \
  || echo "ATTENZIONE: screensaver_components è cambiato! Esegui restore-defaults e segnalalo."
echo "Fatto. Premi Home sul telecomando: deve comparire GlassHome."
