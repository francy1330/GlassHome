#!/bin/sh
# Torna come prima di GlassHome.
#   ./scripts/restore-defaults.sh <IP>               toglie SOLO il servizio Home di GlassHome e
#                                                    ripristina i valori salvati
#   ./scripts/restore-defaults.sh <IP> --uninstall   ...e disinstalla SOLO l'app GlassHome
# Lo screensaver non viene mai modificato: viene solo stampato per controllo.
set -e
. "$(dirname "$0")/lib.sh"
set_device "$1"
connect
show_screensaver "prima"

# 1. Via il nostro servizio, lasciando gli altri come sono.
cur=$(tv settings get secure enabled_accessibility_services)
rest=$(echo "$cur" | tr ':' '\n' | grep -v -x "$SVC" | grep -v -x null | paste -sd: - || true)
if [ -n "$rest" ]; then
  tv settings put secure enabled_accessibility_services "$rest"
else
  o=$(original secure:enabled_accessibility_services)
  if [ -n "$o" ] && [ "$o" != null ] && [ "$o" != "$SVC" ]; then tv settings put secure enabled_accessibility_services "$o"
  else tv settings delete secure enabled_accessibility_services >/dev/null; fi
  # Nessun altro servizio attivo: accessibility_enabled torna al valore originale.
  o=$(original secure:accessibility_enabled)
  [ -n "$o" ] && [ "$o" != null ] && tv settings put secure accessibility_enabled "$o"
fi
echo "enabled_accessibility_services: $(tv settings get secure enabled_accessibility_services)"
echo "accessibility_enabled: $(tv settings get secure accessibility_enabled)"

# 2. Timeout dello screensaver (cambia solo durante i test): torna al valore salvato.
o=$(original system:screen_off_timeout)
if [ -n "$o" ] && [ "$o" != null ] && [ "$(tv settings get system screen_off_timeout)" != "$o" ]; then
  tv settings put system screen_off_timeout "$o"
fi
echo "screen_off_timeout: $(tv settings get system screen_off_timeout)"

# 3. Lo screensaver: solo controllo.
o=$(original secure:screensaver_components)
show_screensaver "dopo"
[ -z "$o" ] || [ "$o" = "$(tv settings get secure screensaver_components)" ] && echo "Screensaver invariato: OK." \
  || echo "Nota: lo screensaver ora è diverso da quello salvato ($o). GlassHome non lo modifica: controlla in Impostazioni > Display."

if [ "$2" = --uninstall ]; then
  "$ADB" -s "$DEV" uninstall "$PKG" && echo "GlassHome disinstallata."
  tv rm -f "$TV_BACKUP"
fi
tv input keyevent KEYCODE_HOME
echo "Fatto: il tasto Home apre di nuovo la home Amazon. Ricordati di spegnere il Debug ADB quando hai finito."
