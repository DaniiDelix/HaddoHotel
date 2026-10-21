# PaperEffortless

Plugin per **Paper 26.2** (Java 25) ispirato alla mod *Effortless Building*: costruisci linee, muri, pavimenti, cubi, cerchi, cilindri e sfere con pochi clic, vedi l'anteprima con i contorni bianchi prima di confermare e usa specchio, simmetria radiale e ripetizione. Funziona senza mod lato giocatore.

## Installazione

1. Copia `PaperEffortless-1.0.0.jar` nella cartella `plugins/` del server.
2. Riavvia il server.
3. La configurazione si trova in `plugins/PaperEffortless/config.yml`.

## Come si usa

1. **Shift + F** (oppure `/eb`) apre il menu: scegli una forma.
2. Prendi un blocco in mano.
3. **Clic destro** su un blocco: è il primo punto.
4. Guarda dove vuoi finire. L'anteprima con i contorni bianchi segue la mira, anche a mezz'aria. **Clic destro** per confermare il punto.
5. Quando hai dato tutti i punti, la forma viene costruita con i blocchi del tuo inventario.
6. **Clic sinistro** annulla la forma in corso.

Sopra la barra degli oggetti vedi la forma scelta, a che punto sei e quanti blocchi verranno piazzati.

### Forme

| Forma | Punti | Come |
|---|---|---|
| Normale | — | Piazzi come sempre, ma con i modificatori attivi |
| Linea | 2 | Inizio, poi la fine lungo uno dei tre assi |
| Muro | 2 | Due angoli opposti di un muro verticale |
| Pavimento | 2 | Due angoli opposti sul piano orizzontale |
| Linea diagonale | 2 | Inizio e fine liberi (la fine è il blocco che guardi) |
| Cubo | 3 | Due angoli della base, poi l'altezza |
| Cerchio | 2 | Centro, poi il raggio |
| Cilindro | 3 | Centro, raggio, poi l'altezza |
| Sfera | 2 | Centro, poi il raggio |

Riempimento: **Pieno**, **Vuoto** (solo il guscio) o **Solo spigoli** (per il cubo).

### Modificatori

Si accendono dal menu e valgono sia per le forme sia per i blocchi piazzati normalmente:

- **Specchio X / Y / Z**: copia simmetrica rispetto al centro. Scale, tronchi e lastre vengono girati nel verso giusto.
- **Simmetria radiale**: 2, 3, 4, 6, 8 o 12 copie attorno al centro.
- **Ripeti**: fino a 16 copie nella direzione in cui guardi quando lo imposti, con il passo scelto.
- **Imposta centro qui**: il centro di specchio e simmetria è il blocco dove ti trovi. Lo segna una particella bianca.

### Annulla e ripeti

Dal menu oppure con `/eb annulla` e `/eb ripeti`, fino a 20 azioni. Annullando, i blocchi tornano nell'inventario.

## Regole

- In sopravvivenza ogni blocco usa un oggetto dall'inventario. Se finiscono, la forma si ferma lì.
- I blocchi vanno solo in spazi liberi (aria, erba, neve…), mai dove c'è un giocatore o un animale.
- Ogni blocco passa per i controlli dei plugin di protezione e di log (WorldGuard, GriefPrevention, CoreProtect…).
- Massimo 4096 blocchi per azione e raggio massimo 48 (modificabili in config).
- Porte, letti e piante alte non si possono usare nelle forme.

## Comandi e permessi

| Comando | Permesso | Descrizione |
|---|---|---|
| `/eb` o `/eb menu` | `effortless.use` | Apre il menu |
| `/eb annulla` | `effortless.use` | Annulla l'ultima azione |
| `/eb ripeti` | `effortless.use` | Rifà l'azione annullata |
| `/eb centro` | `effortless.use` | Centro di specchio e simmetria qui |
| `/eb ricarica` | `effortless.admin` | Ricarica la configurazione |

## Differenze rispetto alla mod

Un plugin non può disegnare interfacce sullo schermo del giocatore, quindi il **menu circolare** è sostituito da un menu a finestra (Shift + F). Per lo stesso motivo le linee di guida dello specchio sono indicate da una particella sul centro.

## Compilare

Servono JDK 25 e Maven: `mvn package` crea `target/PaperEffortless-1.0.0.jar`.
