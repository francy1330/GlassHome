#!/bin/sh
# Verifica che GlassHome conviva con lo screensaver di sistema (Amazon Photos).
#   ./scripts/test-screensaver.sh <IP>
# Porta TEMPORANEAMENTE il timeout dello screensaver a 1 minuto e lo rimette com'era alla fine,
# anche se interrompi con Ctrl+C. Dura circa 5 minuti: in quel tempo non toccare il telecomando.
# Non salva screenshot (lo screensaver mostra le tue foto): controlla tutto con dumpsys.
set -e
. "$(dirname "$0")/lib.sh"
set_device "$1"
connect

ORIG=$(tv settings get system screen_off_timeout)
case "$ORIG" in ""|null) ORIG=$(original system:screen_off_timeout) ;; esac
[ -n "$ORIG" ] || { echo "Non conosco il timeout originale: interrompo." >&2; exit 1; }
restore() {
  tv settings put system screen_off_timeout "$ORIG"
  echo; echo "screen_off_timeout ripristinato: $(tv settings get system screen_off_timeout)"
  show_screensaver "fine test"
}
trap restore EXIT
trap 'exit 130' INT TERM

PASS=0; FAIL=0
ok() { echo "  OK    $*"; PASS=$((PASS + 1)); }
ko() { echo "  FAIL  $*"; FAIL=$((FAIL + 1)); }
wakefulness() { tv dumpsys power | sed -n 's/.*mWakefulness=\([A-Za-z]*\).*/\1/p' | head -1; }
focus() { tv dumpsys window | sed -n 's/.*mCurrentFocus=Window{[^ ]* [^ ]* \([^}]*\)}.*/\1/p' | head -1; }
# Visibilità della finestra overlay del servizio Home: 0x0 visibile, 0x8 nascosta.
overlay() {
  tv dumpsys window windows | awk '/Window #[0-9]+ Window\{[^ ]+ u0 com\.glasshome\.launcher\}:/{f=1}
    f && /mViewVisibility=/{sub(/.*mViewVisibility=/,""); sub(/ .*/,""); print; exit}'
}
our_wakelocks() { tv dumpsys power | sed -n '/^Wake Locks:/,/^$/p' | grep -i glasshome || true; }
keep_screen_on() {
  tv dumpsys window windows | awk '/Window #[0-9]+ /{f=/glasshome/} f && /KEEP_SCREEN_ON/{n++} END{print n+0}'
}
wait_dream() { # secondi fino allo screensaver, o -1 dopo 120 s
  i=0
  while [ $i -lt 24 ]; do
    [ "$(wakefulness)" = Dreaming ] && { echo $((i * 5)); return 0; }
    sleep 5; i=$((i + 1))
  done
  echo -1
}
key() { tv input keyevent "$1"; }
SINCE=$(tv "date '+%m-%d %H:%M:%S.000'")

show_screensaver "inizio test"
echo "screen_off_timeout originale: $ORIG ms  →  60000 ms solo per il test"
tv settings put system screen_off_timeout 60000

echo; echo "1) Controlli statici"
[ -z "$(our_wakelocks)" ] && ok "nessun wakelock di GlassHome" || ko "wakelock di GlassHome: $(our_wakelocks)"
[ "$(keep_screen_on)" = 0 ] && ok "nessuna finestra GlassHome con FLAG_KEEP_SCREEN_ON" || ko "FLAG_KEEP_SCREEN_ON presente"
[ "$(overlay)" = 0x8 ] && ok "overlay del servizio nascosto a riposo" || ko "overlay visibile a riposo ($(overlay))"

echo; echo "2) Home → GlassHome → screensaver → tasto → GlassHome"
key KEYCODE_HOME; sleep 7
case "$(focus)" in *glasshome*MainActivity*) ok "Home apre GlassHome" ;; *) ko "dopo Home in primo piano: $(focus)" ;; esac
[ "$(overlay)" = 0x8 ] && ok "overlay nascosto dopo l'arrivo dell'app" || ko "overlay ancora visibile ($(overlay))"
echo "  ...attendo lo screensaver (non toccare il telecomando)"
t=$(wait_dream)
[ "$t" -ge 0 ] && ok "screensaver partito sopra GlassHome (~${t}s di attesa)" || ko "lo screensaver non è partito in 120 s"
case "$(focus)" in *dream*) ok "in primo piano c'è lo screensaver" ;; *) ko "in primo piano: $(focus)" ;; esac
[ -z "$(our_wakelocks)" ] && ok "nessun wakelock di GlassHome durante lo screensaver" || ko "wakelock: $(our_wakelocks)"
key KEYCODE_BACK; sleep 3
case "$(focus)" in *glasshome*MainActivity*) ok "un tasto chiude lo screensaver e torna a GlassHome" ;; *) ko "dopo il tasto: $(focus)" ;; esac

echo; echo "3) Screensaver che parte con un MENU aperto sull'overlay"
key KEYCODE_HOME; key KEYCODE_MENU; sleep 7
if [ "$(overlay)" = 0x0 ]; then ok "menu aperto sull'overlay (overlay visibile)"
else echo "  nota  il menu si è aperto nell'app e non sull'overlay: ripeto con un tempo più stretto"
  key KEYCODE_BACK; sleep 1; tv "input keyevent KEYCODE_HOME; input keyevent KEYCODE_MENU"; sleep 7
  [ "$(overlay)" = 0x0 ] && ok "menu aperto sull'overlay (overlay visibile)" || ko "non riesco ad aprire il menu sull'overlay ($(overlay))"
fi
echo "  ...attendo lo screensaver con il menu aperto"
t=$(wait_dream)
[ "$t" -ge 0 ] && ok "screensaver partito anche con il menu aperto (~${t}s)" || ko "lo screensaver non è partito in 120 s"
sleep 2
[ "$(overlay)" = 0x8 ] && ok "overlay nascosto: lo screensaver è visibile, non coperto" || ko "OVERLAY SOPRA LO SCREENSAVER ($(overlay))"
case "$(focus)" in *dream*) ok "lo screensaver ha il focus" ;; *) ko "in primo piano: $(focus)" ;; esac
key KEYCODE_BACK; sleep 3
case "$(focus)" in *glasshome*MainActivity*) ok "un tasto chiude lo screensaver e torna a GlassHome" ;; *) ko "dopo il tasto: $(focus)" ;; esac
[ "$(overlay)" = 0x8 ] && ok "overlay resta nascosto dopo lo screensaver" || ko "overlay visibile dopo ($(overlay))"

echo; echo "4) Screensaver con un menu aperto nell'app"
key KEYCODE_MENU; sleep 2
t=$(wait_dream)
[ "$t" -ge 0 ] && ok "screensaver partito con il menu dell'app aperto (~${t}s)" || ko "lo screensaver non è partito in 120 s"
key KEYCODE_BACK; sleep 3
case "$(focus)" in *glasshome*MainActivity*) ok "ritorno a GlassHome" ;; *) ko "dopo il tasto: $(focus)" ;; esac
key KEYCODE_BACK

echo; echo "Log di GlassHome durante il test:"
tv logcat -d -s GlassHome -T "'$SINCE'" | grep -v '^-----' | sed 's/^/  /' | tail -20
echo; echo "Risultato: $PASS OK, $FAIL FAIL"
[ "$FAIL" = 0 ]
