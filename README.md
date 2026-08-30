# UpsWorkPlace V4

V4 collega realmente i campi della giornata al salvataggio locale persistente.

## Cosa cambia
- Ogni campo con `data-key` viene salvato automaticamente su AsyncStorage.
- Driver, codice e targa vengono mantenuti anche per le giornate successive.
- Le pause vengono archiviate come intervalli inizio/fine.
- L'uscita dal magazzino resta bloccata finché l'itinerario non è finalizzato.
- Il modello dati giornaliero rimane la fonte unica per storico, ore, statistiche e Ruolino.
- Il template del Ruolino originale è mantenuto separatamente e non viene ridisegnato.

## Ancora da collegare
- Fotocamera nativa e allegati.
- GPS nativo.
- Schermate complete di storico/Ruolino/statistiche/buste paga.
- Compilazione del PDF del Ruolino tramite coordinate sul template originale.
- Condivisione Android.
