#!/bin/sh
# Controlla che nei file da pubblicare su GitHub non ci siano segreti o dati personali.
#   ./scripts/check-publish.sh
# Da lanciare SEMPRE prima di ogni "git push" su un repository pubblico. Non pubblica niente.
# Esce con errore se trova qualcosa da sistemare.
cd "$(dirname "$0")/.." || exit 1

# 1. L'elenco dei file che finirebbero online.
# Su macOS /usr/bin/git è un segnaposto finché mancano i Command Line Tools: non lo chiamiamo.
has_git() { [ "$(uname)" = Darwin ] && ! xcode-select -p >/dev/null 2>&1 && return 1; command -v git >/dev/null 2>&1; }
if has_git && git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  FILES=$(git ls-files --cached --others --exclude-standard)
  echo "File da pubblicare secondo git: $(echo "$FILES" | wc -l | tr -d ' ')"
else
  # Senza git: stessa logica del .gitignore, a mano.
  FILES=$(find . -type f \
    -not -path './.git/*' -not -path './toolchain/*' -not -path './_reference/*' -not -path './_downloads/*' \
    -not -path './_shots/*' -not -path './backups/*' -not -path './out/*' -not -path './dist/*' \
    -not -name 'keystore.properties' -not -name '*.jks' -not -name '*.keystore' -not -name '*.idsig' \
    -not -name '.DS_Store' | sed 's|^\./||')
  echo "File da pubblicare (git non disponibile, uso le regole del .gitignore): $(echo "$FILES" | wc -l | tr -d ' ')"
fi

FAIL=0
bad() { echo "  DA SISTEMARE  $*"; FAIL=1; }

# 2. File che non devono mai uscire.
echo "$FILES" | grep -E '(^|/)(keystore\.properties|[^/]*\.jks|[^/]*\.keystore|[^/]*\.apk|[^/]*\.idsig)$|^(backups|_shots|toolchain|_downloads)/' \
  | while read -r f; do bad "file privato o generato: $f"; done | grep . && FAIL=1

# 3. Contenuti sospetti nei file di testo.
ME=$(whoami)
check() { # $1 = descrizione, $2 = regex (grep -E), $3 = regex da ignorare (facoltativa)
  hits=$(echo "$FILES" | while read -r f; do [ -f "$f" ] && grep -I -n -H -E "$2" "$f" 2>/dev/null; done)
  [ -n "$3" ] && hits=$(echo "$hits" | grep -v -E "$3")
  if [ -n "$hits" ]; then
    echo "$hits" | head -5 | while read -r h; do bad "$1: $h"; done
    return 1
  fi
}
check "password della chiave scritta in chiaro" '(storePassword|keyPassword)=[A-Za-z0-9]{6,}' || FAIL=1
check "chiave privata" 'BEGIN (RSA |EC )?PRIVATE KEY' || FAIL=1
check "indirizzo IP della rete di casa" '(^|[^0-9])(192\.168|10\.[0-9]+|172\.(1[6-9]|2[0-9]|3[01]))\.[0-9]+\.[0-9]+' '192\.168\.1\.50' || FAIL=1
check "indirizzo email" '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}' 'noreply|users\.noreply\.github\.com|@anthropic\.com|example\.(com|org)' || FAIL=1
check "percorso della tua cartella utente" "/Users/$ME|C:\\\\Users\\\\$ME" || FAIL=1

# 4. L'email con cui firmi i commit (finisce nello storico pubblico).
if has_git; then
  email=$(git config user.email 2>/dev/null)
  case "$email" in
    "") echo "  nota  git non ha ancora un'email: imposta quella anonima di GitHub (…@users.noreply.github.com)" ;;
    *users.noreply.github.com) echo "  OK    email dei commit anonima: $email" ;;
    *) bad "i commit userebbero l'email \"$email\": usa quella anonima di GitHub (…@users.noreply.github.com)" ;;
  esac
fi

echo
if [ "$FAIL" = 0 ]; then echo "Nessun segreto o dato personale trovato: si può pubblicare."
else echo "Sistema i punti sopra prima di pubblicare."; exit 1; fi
