#!/bin/sh
# Funzioni comuni agli script di GlassHome (macOS / Linux). Non si esegue da solo.

PKG=com.glasshome.launcher
SVC=$PKG/$PKG.HomeService
ROOT=$(cd "$(dirname "$0")/.." && pwd)
BACKUPS=$ROOT/backups
TV_BACKUP=/data/local/tmp/glasshome-backup.properties
# Le impostazioni che salviamo prima di toccare qualsiasi cosa.
KEYS="secure:enabled_accessibility_services secure:accessibility_enabled secure:screensaver_components system:screen_off_timeout"

ADB=""
for a in "$ROOT/toolchain/sdk/platform-tools/adb" "$(command -v adb 2>/dev/null)" \
         /opt/homebrew/bin/adb /usr/local/bin/adb "$HOME/Library/Android/sdk/platform-tools/adb"; do
  [ -n "$a" ] && [ -x "$a" ] && { ADB=$a; break; }
done
[ -n "$ADB" ] || { echo "adb non trovato. Vedi README.md, sezione 'Cosa serve'." >&2; exit 1; }

# $1 = IP della Fire TV (con o senza :5555)
set_device() {
  [ -n "$1" ] || { echo "Manca l'indirizzo IP della Fire TV (Impostazioni > La mia Fire TV > Informazioni > Rete)." >&2; exit 1; }
  case "$1" in *:*) DEV=$1 ;; *) DEV=$1:5555 ;; esac
  SAFE=$(echo "$DEV" | tr ':.' '__')
}

tv() { "$ADB" -s "$DEV" shell "$@" </dev/null | tr -d '\r'; }

connect() {
  "$ADB" connect "$DEV" >/dev/null 2>&1 || true
  state=$("$ADB" -s "$DEV" get-state 2>/dev/null || true)
  if [ "$state" != device ]; then
    echo "Guarda la TV: scegli \"Consenti sempre da questo computer\" e premi OK."
    i=0
    while [ "$state" != device ] && [ $i -lt 45 ]; do
      sleep 2; i=$((i + 1))
      "$ADB" connect "$DEV" >/dev/null 2>&1 || true
      state=$("$ADB" -s "$DEV" get-state 2>/dev/null || true)
    done
  fi
  [ "$state" = device ] || { echo "Non riesco a collegarmi a $DEV. Il Debug ADB è attivo? (Impostazioni > La mia Fire TV > Opzioni sviluppatore)" >&2; exit 1; }
  echo "Collegato a $(tv getprop ro.product.model), $(tv getprop ro.build.version.name)"
}

getset() { tv settings get "${1%%:*}" "${1#*:}"; }

# Salva le impostazioni attuali. Il PRIMO backup (original-*) non viene mai sovrascritto:
# è quello che restore-defaults ripristina. Una copia resta anche sulla TV.
backup_settings() {
  mkdir -p "$BACKUPS"
  snap=$BACKUPS/$SAFE-$(date +%Y%m%d-%H%M%S).properties
  {
    echo "# GlassHome: impostazioni di $DEV il $(date)"
    for k in $KEYS; do echo "$k=$(getset "$k")"; done
  } > "$snap"
  orig=$BACKUPS/original-$SAFE.properties
  if [ ! -f "$orig" ]; then
    if tv "[ -f $TV_BACKUP ] && echo yes" | grep -q yes; then
      tv cat "$TV_BACKUP" > "$orig" # un'installazione precedente da un altro computer
      echo "Backup originale recuperato dalla TV: $orig"
    else
      cp "$snap" "$orig"
      "$ADB" -s "$DEV" push "$orig" "$TV_BACKUP" >/dev/null
      echo "Backup originale salvato: $orig (e sulla TV in $TV_BACKUP)"
    fi
  fi
  echo "Istantanea: $snap"
}

# Valore salvato nel backup originale ("" se assente).
original() {
  orig=$BACKUPS/original-$SAFE.properties
  [ -f "$orig" ] || { tv "[ -f $TV_BACKUP ] && cat $TV_BACKUP" > "$orig" 2>/dev/null; [ -s "$orig" ] || rm -f "$orig"; }
  [ -f "$orig" ] && sed -n "s/^$1=//p" "$orig" | head -1
}

show_screensaver() {
  echo "screensaver_components ($1): $(tv settings get secure screensaver_components)"
}
