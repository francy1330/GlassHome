# GlassHome

**Una home per Amazon Fire TV Stick ispirata a tvOS, senza pubblicità.**
Niente root, nessuna parte di Fire OS viene disattivata e tutto si può annullare.

🇬🇧 [Read in English](README.md)

- Una foto dello spazio a tutto schermo che sfuma in un **dock di vetro** con le tue app preferite
- **Focus in stile tvOS**: la tessera si solleva con una molla, fa ombra, si inclina e riflette la luce
- **App recenti**, **tutte le app** sotto, e puoi **nascondere** quelle che non usi mai
- **Sfondi del cosmo** di James Webb e Hubble, uno nuovo ogni giorno se vuoi
- **Meteo**, stato del **Wi-Fi**, **ricerca**, scorciatoie alle impostazioni della Fire TV
- **Convive con lo screensaver di Amazon** (Amazon Photos): parte sempre, e dopo ritrovi GlassHome
- **Backup e ripristino** della tua configurazione dal computer

> **Progetto personale, senza assistenza.** Lo condivido così com'è, con licenza MIT e senza garanzie.
> Basato su [FireFrame](https://github.com/dyascj/FireFrame). Non è affiliato ad Amazon né ad Apple.

Testato su Fire TV Stick 3ª gen. (AFTSSS), Fire OS 7.7.1.7. Fire OS 8 dovrebbe funzionare ma non è
testato. Le Fire TV con **Vega OS** (es. Fire TV Stick 4K Select) non possono usarlo.

---

## Come funziona, in un minuto

Fire OS non permette di sostituire la home di Amazon, e GlassHome non ci prova. Si installa come
una normale app, più un piccolo **servizio di accessibilità** che si accorge quando si apre la home
Amazon (per esempio premendo Home) e mostra subito GlassHome sopra. Il launcher Amazon resta attivo
in silenzio, dietro.

Si installa **da un computer** (Mac o Windows) collegato allo stesso Wi-Fi della Fire TV, con uno
strumento gratuito di Google che si chiama **adb**. La prima volta ci vogliono circa 15 minuti. Il
computer serve solo per installare, aggiornare e per alcuni extra facoltativi: ogni giorno si usa
il telecomando.

---

## Passo 1 · Prepara la Fire TV (5 minuti)

1. Sulla Fire TV apri **Impostazioni → La mia Fire TV → Informazioni**.
2. Seleziona il nome del dispositivo (es. "Fire TV Stick") e premi **OK 7 volte**, finché ti dice che sei uno sviluppatore.
3. Torna indietro: sotto La mia Fire TV compare **Opzioni sviluppatore**. Aprila e attiva **Debug ADB**.
4. Trova l'**indirizzo IP** della Fire TV: **Impostazioni → La mia Fire TV → Informazioni → Rete**.
   È un numero come `192.168.1.50`. Segnatelo: ti servirà più avanti.

## Passo 2 · Scarica GlassHome (2 minuti)

1. In questa pagina clicca il pulsante verde **Code** → **Download ZIP**, poi decomprimilo.
   Ottieni una cartella `GlassHome-main` (puoi rinominarla `GlassHome`).
2. Apri **[Releases](../../releases/latest)** e scarica `GlassHome-<versione>.apk`.
3. Dentro la cartella GlassHome crea una cartella chiamata **`dist`** e mettici il file `.apk`.

## Passo 3 · Procurati adb (5 minuti)

adb è lo strumento ufficiale di Google per parlare con i dispositivi Android.

1. Scarica **SDK Platform-Tools** per il tuo sistema da
   <https://developer.android.com/tools/releases/platform-tools> e decomprimilo.
   Ottieni una cartella `platform-tools`.
2. Dentro la cartella GlassHome crea la cartella **`toolchain`** e, al suo interno, **`sdk`**,
   poi spostaci `platform-tools`. Il risultato deve essere:
   `GlassHome/toolchain/sdk/platform-tools/adb` (su Windows `adb.exe`).

> Se hai già adb (es. `brew install android-platform-tools` su Mac), salta questo passo: gli script
> lo trovano da soli.

## Passo 4 · Apri un terminale nella cartella GlassHome

**Mac**
1. Apri il **Terminale** (Applicazioni → Utility → Terminale).
2. Scrivi `cd ` (con uno spazio dopo), trascina la cartella GlassHome nella finestra del Terminale e premi **Invio**.

**Windows**
1. Apri la cartella GlassHome in Esplora file.
2. Clicca sulla barra dell'indirizzo, scrivi `powershell` e premi **Invio**. Si apre una finestra blu già in quella cartella.

## Passo 5 · Installa

Sostituisci `192.168.1.50` con l'IP della tua Fire TV.

**Mac**
```bash
sh scripts/install.sh 192.168.1.50
```

**Windows**
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\install.ps1 192.168.1.50
```

La prima volta la TV chiede **"Consentire il debug USB?"**: scegli **Consenti sempre da questo
computer** e premi OK. Quando lo script dice *Fatto*, premi **Home** sul telecomando: compare GlassHome.

Cosa fa `install`: salva le impostazioni che potrebbe toccare (nella cartella `backups` e sulla TV),
installa l'app, attiva il servizio "GlassHome - tasto Home" senza toccare gli altri servizi di
accessibilità, e stampa l'impostazione dello screensaver prima e dopo per dimostrare che non è cambiata.

> **Solo Mac:** se macOS dice *impossibile aprire "adb" perché lo sviluppatore non può essere
> verificato*, apri **Impostazioni di Sistema → Privacy e sicurezza**, clicca **Apri comunque** e
> rilancia il comando.

---

## Usarlo con il telecomando

| Tasto | Cosa fa |
| --- | --- |
| ◀ ▶ ▲ ▼ | ti muovi; la tessera col focus si solleva con un piccolo rimbalzo e un riflesso |
| OK | apri l'app |
| Tieni premuto OK, o ☰ Menu | menu dell'app: **Sposta**, aggiungi/togli dal dock, **Sistema icona…**, nascondi, Info app |
| ▼ dal dock | **Recenti**, poi **Tutte le app** |
| ▲ dal dock | barra in alto: **Cerca**, **Impostazioni**, **Home Amazon**, **Opzioni** |
| ☰ Menu (non su una tessera) | Opzioni |
| Indietro | chiude un menu, o torna al dock |
| Home | torna sempre a GlassHome, sul dock |

- **Spostare un'app nel dock:** tieni premuto OK → Sposta → ◀ ▶ → OK.
- **Nascondere un'app:** in Tutte le app, tieni premuto OK → Nascondi dal cassetto. Per rimostrarla:
  Opzioni → App nascoste. L'app resta installata e funziona come sempre.
- **Cerca** apre la ricerca di Fire TV (la scheda "Trova" di Amazon): premi ▼ e OK per scrivere.
  Alexa funziona sempre dal pulsante del microfono del telecomando (Fire OS non permette ad altre app di avviarla).
- **Impostazioni** ha scorciatoie a Rete e Wi-Fi, Telecomandi e Bluetooth, Applicazioni, La mia Fire TV e Tutte le impostazioni.
- **Opzioni:** sfondo, sfondo del giorno, angoli delle tessere, app nascoste, memoria ("chiudi le app
  in background"), meteo, svuota i recenti, informazioni.
- **Sistema icona** (tieni premuto OK su un'app): *Automatica* (tessera generata dall'icona),
  *Carica dal PC* (ti mostra il comando `tile`), *Ripristina originale* (banner TV dell'app).

## Extra facoltativi (dal computer)

Su Mac usa `sh scripts/NOME.sh <IP> …`, su Windows
`powershell -ExecutionPolicy Bypass -File .\scripts\NOME.ps1 <IP> …`.

| Cosa | Esempio su Mac |
| --- | --- |
| Meteo della tua città | `sh scripts/weather.sh 192.168.1.50 "Milano"` (per toglierlo: `off`) |
| Una tua foto come sfondo (ridotta a 1080p) | `sh scripts/wallpaper.sh 192.168.1.50 ~/Pictures/foto.jpg` |
| Elenco dei package delle app installate | `sh scripts/tile.sh 192.168.1.50 list` |
| Tessera 16:9 personalizzata per un'app | `sh scripts/tile.sh 192.168.1.50 com.netflix.ninja netflix.png` |
| Backup della configurazione | `sh scripts/backup-config.sh 192.168.1.50` |
| Ripristina l'ultimo backup | `sh scripts/restore-config.sh 192.168.1.50` |
| Verifica che lo screensaver funzioni (~5 min) | `sh scripts/test-screensaver.sh 192.168.1.50` |

## Tornare indietro

```bash
sh scripts/restore-defaults.sh 192.168.1.50 --uninstall
```
(Windows: `powershell -ExecutionPolicy Bypass -File .\scripts\restore-defaults.ps1 192.168.1.50 -Uninstall`)

Toglie **solo** il servizio e l'app di GlassHome e rimette le impostazioni salvate. Senza
`--uninstall` tiene l'app ma restituisce il tasto Home ad Amazon.

Senza computer: **Impostazioni → Applicazioni → Gestisci applicazioni installate → GlassHome →
Disinstalla**. Quando hai finito con gli script puoi spegnere di nuovo il **Debug ADB**.

## Aggiornare

Scarica il nuovo `.apk` dalle Releases nella cartella `dist`, poi rilancia `install`. Dock e
impostazioni restano.

## Se qualcosa non va

- **"Non riesco a collegarmi"**: computer e Fire TV devono essere sullo stesso Wi-Fi; controlla che
  il Debug ADB sia attivo e che l'IP sia giusto (può cambiare dopo un riavvio del router).
- **La TV non ha chiesto di consentire il debug**: sveglia la TV (premi un tasto) e rilancia il comando.
- **Dopo un aggiornamento di Fire OS il tasto Home riapre la home Amazon**: rilancia `install`.
  GlassHome è comunque sempre apribile dall'elenco delle app.
- **Lo screensaver non parte**: GlassHome non lo modifica mai. Lancia `test-screensaver` per
  controllare (porta il ritardo dello screensaver a 1 minuto solo durante il test, poi lo rimette com'era).

## Privacy

Niente pubblicità, niente tracciamento, nessun account. L'unica connessione a internet è il meteo
facoltativo (Open-Meteo), e solo dopo che hai scelto una città. I backup restano sul tuo computer.

---

## Per chi sviluppa

- Java e view Android, nessuna libreria, niente Gradle: `./build.sh` (Mac/Linux) o `.\build.ps1`
  (Windows) crea `dist/GlassHome-<versione>.apk` con JDK 17 e Android SDK (`platforms;android-34`,
  `build-tools;34.0.0`). La prima build crea **la tua** chiave di firma in `~/keystores/` e la sua
  password in `keystore.properties` (entrambi esclusi da git): **fanne una copia**, perché Android
  accetta gli aggiornamenti solo se sono firmati con la stessa chiave.
- Codice: `app/src/com/glasshome/launcher/` — `HomeScreen` (UI), `TileView` (effetti del focus),
  `Glass` (vetro simulato), `HomeService` (tasto Home e convivenza con lo screensaver), `tiles/`
  (sorgenti delle tessere: file personalizzato → banner TV dell'app → tessera generata; per
  estenderle basta aggiungere una nuova `TileSource`).
- Prima di caricare su un repository pubblico lancia `./scripts/check-publish.sh`: cerca chiavi,
  password, IP di casa, indirizzi email e percorsi personali.

Licenza MIT. Font, icone e foto mantengono le proprie licenze: vedi
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
