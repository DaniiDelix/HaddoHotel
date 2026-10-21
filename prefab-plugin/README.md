# PaperPrefab

Plugin per **Paper 26.2** (Java 25) ispirato alla mod *Prefab*: oggetti craftabili che costruiscono una struttura pronta con un clic, con anteprima "fantasma" prima di confermare. Funziona senza mod lato giocatore.

## Installazione

1. Copia `PaperPrefab-1.0.0.jar` nella cartella `plugins/` del server.
2. Riavvia il server (non usare `/reload`).
3. La configurazione si trova in `plugins/PaperPrefab/config.yml`.

## Come si usa

1. Prendi un oggetto prefab (crafting oppure `/prefab dai`).
2. **Clic destro** sul terreno: compare l'anteprima. Il bordo è **verde** se si può costruire, **rosso** se c'è un problema (il motivo appare in chat).
3. **Clic destro di nuovo sullo stesso punto** per costruire. Se miri un altro punto, l'anteprima si sposta.
4. **Shift + clic destro** (oppure `/prefab stop`, o cambiare oggetto in mano) chiude l'anteprima.
5. `/prefab annulla` annulla l'ultima costruzione entro 10 minuti e restituisce l'oggetto. Il contenuto di casse e barili viene lasciato cadere a terra.

La struttura si costruisce **davanti a te**, con l'ingresso rivolto verso di te, partendo dal blocco che guardi. I buchi sotto la struttura vengono riempiti (fondamenta), erba e fiori vengono tolti e, se attivo in config, anche terra, pietra, sabbia e foglie.

## Strutture incluse e ricette

| Id | Struttura | Ricetta (3×3, riga per riga) |
|---|---|---|
| `casetta` | Casetta 7×7 con tetto, letto, cassa, fornace, banco da lavoro | Tronco · Vetro · Tronco / Ciottolato · Porta · Ciottolato / Tronco · Letto · Tronco |
| `fattoria` | Campo di grano 9×9 con acqua, recinto e cancello | Staccionata · Semi · Staccionata / Semi · Secchio d'acqua · Semi / Staccionata · Zappa · Staccionata |
| `magazzino` | Stanza in mattoni di pietra con 22 barili | Mattoni di pietra · Barile · Mattoni / Barile · Lanterna · Barile / Mattoni · Barile · Mattoni |
| `torre` | Torre di guardia alta 12 con scala e terrazza | Mattoni · Staccionata · Mattoni / Mattoni · Scala a pioli · Mattoni / Mattoni · Porta · Mattoni |
| `recinto` | Recinto 9×9 per animali con fieno e abbeveratoio | Staccionata ×3 / Staccionata · Balla di fieno · Staccionata / Staccionata · Cancello · Staccionata |

Le ricette si possono disattivare con `ricette.attive: false`.

## Strutture personalizzate

Costruisci qualcosa in gioco, poi:

1. Guarda un angolo e scrivi `/prefab pos1`, guarda l'angolo opposto e scrivi `/prefab pos2`.
2. Mettiti **davanti alla facciata** (dove vuoi l'ingresso), guardando la costruzione, e scrivi `/prefab salva <id> [nome]`.
3. Dai l'oggetto con `/prefab dai <giocatore> <id>`.

Le strutture vengono salvate come file `.nbt` vanilla in `plugins/PaperPrefab/strutture/` (massimo 48×48×48). Il contenuto delle casse non viene copiato.

## Comandi e permessi

| Comando | Permesso | Descrizione |
|---|---|---|
| `/prefab lista` | `prefab.use` | Strutture disponibili |
| `/prefab stop` | `prefab.use` | Chiude l'anteprima |
| `/prefab annulla` | `prefab.use` | Annulla l'ultima costruzione |
| `/prefab dai <giocatore> <id> [quantità]` | `prefab.admin` | Dà oggetti prefab |
| `/prefab pos1` / `pos2` | `prefab.admin` | Angoli per salvare una struttura |
| `/prefab salva <id> [nome]` | `prefab.admin` | Salva una struttura personalizzata |
| `/prefab elimina <id>` | `prefab.admin` | Elimina una struttura personalizzata |
| `/prefab ricarica` | `prefab.admin` | Ricarica config e strutture |

`prefab.use` è attivo per tutti, `prefab.admin` solo per gli op. Prima di costruire, il plugin chiede ai plugin di protezione (WorldGuard, GriefPrevention, …) se il giocatore può costruire in quella zona.

## Compilare

Servono JDK 25 e Maven:

```
mvn package
```

Il file viene creato in `target/PaperPrefab-1.0.0.jar`.
