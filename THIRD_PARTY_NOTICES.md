# Crediti e licenze di terze parti

GlassHome è un fork di **[FireFrame](https://github.com/dyascj/FireFrame)** di CJ Dyas (licenza MIT, vedi [LICENSE](LICENSE)):
da FireFrame vengono il servizio del tasto Home (overlay di accessibilità + broadcast
`com.amazon.tv.launcher.HOME_PRESSED`), lo script di build senza Gradle e gli sfondi.
L'idea di riportare GlassHome davanti al risveglio (`ACTION_SCREEN_ON`) viene dal launcher di
**[dumbtv](https://github.com/humblemuzzu/dumbtv)**. Rimossi rispetto a FireFrame: debloat, aggiornamento automatico, permesso INTERNET, loghi delle app.

| Risorsa | Fonte | Licenza |
| --- | --- | --- |
| Font Inter Medium e SemiBold (`app/assets/fonts`) | [rsms/inter](https://github.com/rsms/inter) | SIL Open Font License 1.1 (`app/assets/fonts/Inter-LICENSE.txt`) |
| Icone UI: image, grid_view, settings (`app/assets/ui`) | [Material Symbols](https://github.com/google/material-design-icons) | Apache License 2.0 |
| Sfondi (`app/assets/wallpapers`) | [NASA Image and Video Library](https://images.nasa.gov) | Pubblico dominio (linee guida NASA) |
| StackBlur (`tiles/Bitmaps.java`) | algoritmo di Mario Klingemann | uso libero, con attribuzione |
| Icona e banner di GlassHome | generati da `tools/MakeArt.java` | MIT (questo progetto) |

Sfondi (NASA Image and Video Library, https://images.nasa.gov — ID tra parentesi), ritagliati a 1920×1080:

| File | Soggetto | Crediti |
| --- | --- | --- |
| cosmiccliffs.jpg | "Cosmic Cliffs", Nebulosa della Carena (carina_nebula) | NASA, ESA, CSA, STScI — James Webb |
| pillars.jpg | Pilastri della Creazione nell'infrarosso (GSFC_20171208_Archive_e000842) | NASA, ESA, Hubble Heritage Team |
| deepfield.jpg | Hubble Ultra Deep Field (GSFC_20171208_Archive_e001061) | NASA, ESA |
| lagoon.jpg | Il cuore della Nebulosa Laguna (GSFC_20171208_Archive_e001955) | NASA, ESA |
| mysticmountain.jpg | Mystic Mountain, Carena (GSFC_20171208_Archive_e002074) | NASA, ESA, M. Livio, Hubble 20th Anniversary Team |
| crab.jpg | Nebulosa del Granchio (PIA03606) | NASA, ESA, J. Hester, A. Loll |
| bubble.jpg | Nebulosa Bolla (GSFC_20171208_Archive_e000383) | NASA, ESA, Hubble Heritage Team |
| horsehead.jpg | Testa di Cavallo nell'infrarosso (PIA16008) | NASA, ESA, Hubble Heritage Team |
| earthrise.jpg | Earthrise, Apollo 17 (as17-152-23272, da FireFrame) | NASA |

Le immagini NASA sono di pubblico dominio secondo le linee guida NASA; le immagini realizzate con
ESA/STScI sono riutilizzabili citando i crediti, come sopra e nella didascalia mostrata da GlassHome.
NASA, ESA, CSA e STScI non sostengono questo progetto.

Meteo: dati di [Open-Meteo](https://open-meteo.com) (CC BY 4.0).

GlassHome non è affiliato ad Amazon né ad Apple. Lo stile è "ispirato a tvOS"; non usa marchi né font Apple.
I banner delle app mostrati sulle tessere sono forniti dalle app stesse installate sul dispositivo.
