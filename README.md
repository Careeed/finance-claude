# Entrate & Spese — v2.1

App Android (funziona anche offline) per gestire entrate, spese e conti di Simo e Paola,
con i dati storici importati da `source.xlsx` (aprile 2023 → settembre 2026) e sincronizzazione
facoltativa tra due telefoni tramite un Foglio Google.

## Schermate
Home · Nuova spesa / Nuova entrata (pulsante ＋) · Movimenti (filtri, modifica, elimina) ·
Conti · Statistiche · Impostazioni (categorie, conti, utenti/password, sincronizzazione, tema).

## Come è fatta
- `app/src/main/assets/index.html` — interfaccia e logica (HTML/CSS/JS, nessuna dipendenza esterna)
- `app/src/main/assets/seed.js` — dati iniziali generati dall'Excel con `python3 tools/build_seed.py`
- `MainActivity.java` — guscio Android: mostra la pagina, salva i dati in un file privato,
  backup/ripristino su file, chiamate di rete per la sincronizzazione
- `tools/apps-script/Code.gs` — script lato Google Sheets che riceve e unisce i dati dei due telefoni

## Build dell'APK
GitHub → **Actions → Build APK → Run workflow**, poi scarica l'artifact `Entrate-Spese-debug`.
L'APK è firmato con `app/debug.keystore` (incluso nel repo): gli aggiornamenti successivi
si installano sopra la versione precedente senza perdere i dati.

## Il tuo script già distribuito

https://script.google.com/macros/s/AKfycbzo96GzaqHOuHBfqqXbpi8cvTQ-DTNmbuSRDsJWlKPstmJ20NQeLQb-w1SPb1eqIZkf/exec

Usalo com'è (Impostazioni → Collega Fogli Google) sui due telefoni, con la parola segreta che hai impostato tu nel codice. Se in futuro modifichi `Code.gs`, ricordati di fare "Nuova versione" nella distribuzione, altrimenti l'app continua a usare questa.

## Attivare la sincronizzazione tra i due telefoni (facoltativa)
1. Crea un nuovo Foglio Google vuoto (sheets.new).
2. Menu **Estensioni → Apps Script**. Cancella il contenuto e incolla tutto `tools/apps-script/Code.gs`.
3. Nella prima riga del codice, cambia `CAMBIA-QUESTA-PAROLA-SEGRETA` con una password a tua scelta:
   usala identica su entrambi i telefoni, è ciò che impedisce a chiunque altro di scrivere nel foglio.
4. **Distribuisci → Nuova distribuzione → tipo "App web"**. Esegui come **Io**, accesso **Chiunque**.
   Autorizza i permessi quando richiesto. Copia l'indirizzo che termina con `/exec`.
5. Nell'app, su entrambi i telefoni: **Impostazioni → Collega Fogli Google**, incolla lo stesso
   indirizzo e la stessa password, scegli se includere anche le spese personali, poi **Salva e sincronizza**.
6. Da quel momento l'app sincronizza da sola quando riapri l'app o dopo ogni modifica (con qualche
   secondo di ritardo); puoi anche forzarla con "Sincronizza ora".

Da notare:
- Le **password di Simo e Paola non vengono mai sincronizzate**: vanno impostate separatamente
  su ogni telefono (per sicurezza, restano solo sul dispositivo).
- Se scegli di sincronizzare anche le spese personali, finiscono nel Foglio Google in chiaro,
  leggibili da chi ha accesso al foglio.
- Nel foglio compare anche una scheda leggibile **"Movimenti app"**, in sola lettura,
  utile per dare un'occhiata ai movimenti registrati dall'app.
- Se modifichi di nuovo `Code.gs` in futuro, dopo aver incollato le modifiche vai su
  **Distribuisci → Gestisci distribuzioni → Modifica (icona matita) → Nuova versione → Distribuisci**,
  altrimenti l'app continuerà a usare la versione precedente dello script.
