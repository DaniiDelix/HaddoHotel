# PaperWands

Plugin per **Paper 26.2** (Java 25) ispirato alla mod *Building Wands*: bacchette che, con un clic, piazzano copie del blocco cliccato su tutta la superficie collegata, usando i blocchi dell'inventario. Funziona senza mod lato giocatore.

## Installazione

1. Copia `PaperWands-1.0.0.jar` nella cartella `plugins/` del server.
2. Riavvia il server.
3. La configurazione si trova in `plugins/PaperWands/config.yml`.

## Come si usa

- **Tieni in mano la bacchetta e guarda un blocco**: delle particelle verdi mostrano dove verranno messi i blocchi.
- **Clic destro** sul blocco: piazza le copie su quel lato. Le copie hanno la stessa rotazione dell'originale (tronchi, scale, lastre…).
- **Clic sinistro**: cambia modalità. Con la bacchetta in mano il clic sinistro non rompe i blocchi.
- `/bacchetta annulla` annulla l'ultima piazzata entro 5 minuti e restituisce i blocchi.

### Modalità

| Modalità | Cosa fa |
|---|---|
| Superficie (stesso blocco) | Copre tutti i blocchi collegati dello **stesso tipo** di quello cliccato |
| Superficie (qualsiasi blocco) | Copre tutti i blocchi solidi collegati, di qualsiasi tipo |
| Linea | Una fila dritta nella direzione in cui guardi |
| Singolo | Solo un blocco, copiando la rotazione |

La bacchetta non piazza blocchi dove c'è un giocatore o un animale, e si ferma davanti agli ostacoli. Porte, letti e piante alte non si possono copiare.

## Bacchette

| Tipo | Blocchi per clic | Durata | Ricetta |
|---|---|---|---|
| Pietra | 9 | 131 | Ciottolato in alto a destra + 2 bastoni in diagonale |
| Ferro | 25 | 250 | Lingotto di ferro + 2 bastoni |
| Diamante | 64 | 1561 | Diamante + 2 bastoni |
| Netherite | 128 | 2031 | Lingotto di netherite + 2 bastoni |
| Creativa | 256 | infinita | Solo con `/bacchetta dai`, non usa blocchi |

Ogni blocco piazzato consuma 1 punto di durata. In modalità creativa non si consumano né blocchi né durata. Valori, nomi e aspetto si cambiano in `config.yml`.

## Comandi e permessi

| Comando | Permesso | Descrizione |
|---|---|---|
| `/bacchetta annulla` | `wands.use` | Annulla l'ultima piazzata |
| `/bacchetta dai <giocatore> <tipo>` | `wands.admin` | Dà una bacchetta |
| `/bacchetta ricarica` | `wands.admin` | Ricarica la configurazione |

Ogni blocco piazzato passa per i controlli dei plugin di protezione e di log (WorldGuard, GriefPrevention, CoreProtect, …), come se l'avesse messo il giocatore a mano.

## Compilare

Servono JDK 25 e Maven: `mvn package` crea `target/PaperWands-1.0.0.jar`.
