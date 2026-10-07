/**
 * Sincronizzazione "Entrate & Spese" con Fogli Google.
 *
 * Installazione: nel Foglio Google  Estensioni > Apps Script > incolla questo codice,
 * cambia TOKEN qui sotto, poi Distribuisci > Nuova distribuzione > App web
 * (Esegui come: Io · Chi ha accesso: Chiunque) e copia l'URL che termina con /exec.
 * Se in seguito modifichi il codice: Distribuisci > Gestisci distribuzioni > Modifica > Nuova versione.
 */
const TOKEN = 'CAMBIA-QUESTA-PAROLA-SEGRETA';
const NOMI = { S: 'Simo', P: 'Paola', C: 'Casa' };
const CHUNK = 40000;

function doGet() {
  return ContentService.createTextOutput('Sincronizzazione Entrate & Spese attiva.');
}

function doPost(e) {
  const lock = LockService.getScriptLock();
  lock.waitLock(25000);
  try {
    const req = JSON.parse(e.postData.contents);
    if (req.token !== TOKEN) return reply({ error: 'Token non valido' });
    const st = readStore();
    mergeInto(st, req);
    writeStore(st);
    mirror(st);
    return reply({
      ok: true,
      movs: Object.values(st.movs),
      tomb: st.tomb,
      accounts: Object.values(st.accounts),
      extraCats: st.extraCats
    });
  } catch (err) {
    return reply({ error: String(err) });
  } finally {
    lock.releaseLock();
  }
}

function reply(o) {
  return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON);
}

function mergeInto(st, req) {
  const tomb = req.tomb || {};
  for (const id in tomb) {
    const ts = tomb[id];
    if (!st.tomb[id] || st.tomb[id] < ts) st.tomb[id] = ts;
    if (st.movs[id] && (st.movs[id].u || 0) <= ts) delete st.movs[id];
  }
  (req.movs || []).forEach(function (m) {
    if (st.tomb[m.id] && st.tomb[m.id] >= (m.u || 0)) return;
    const o = st.movs[m.id];
    if (!o || (m.u || 0) > (o.u || 0)) st.movs[m.id] = m;
  });
  (req.accounts || []).forEach(function (a) {
    const o = st.accounts[a.id];
    if (!o || (a.bu || 0) > (o.bu || 0)) st.accounts[a.id] = a;
  });
  ['o', 'i'].forEach(function (t) {
    ((req.extraCats || {})[t] || []).forEach(function (c) {
      if (st.extraCats[t].indexOf(c) < 0) st.extraCats[t].push(c);
    });
  });
}

function emptyStore() {
  return { movs: {}, tomb: {}, accounts: {}, extraCats: { o: [], i: [] } };
}

function syncSheet() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sh = ss.getSheetByName('_sync');
  if (!sh) { sh = ss.insertSheet('_sync'); sh.hideSheet(); }
  return sh;
}

function readStore() {
  const sh = syncSheet();
  const n = sh.getLastRow();
  if (n < 1) return emptyStore();
  const txt = sh.getRange(1, 1, n, 1).getValues().map(function (r) { return r[0]; }).join('');
  try { return JSON.parse(txt); } catch (e) { return emptyStore(); }
}

function writeStore(st) {
  const sh = syncSheet();
  const txt = JSON.stringify(st);
  const rows = [];
  for (let i = 0; i < txt.length; i += CHUNK) rows.push([txt.substring(i, i + CHUNK)]);
  sh.clear();
  const r = sh.getRange(1, 1, rows.length, 1);
  r.setNumberFormat('@');
  r.setValues(rows);
}

/** Foglio leggibile con i movimenti registrati dall'app (nuovi o modificati). */
function mirror(st) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sh = ss.getSheetByName('Movimenti app');
  if (!sh) sh = ss.insertSheet('Movimenti app', 0);
  const nome = {};
  Object.values(st.accounts).forEach(function (a) { nome[a.id] = a.name; });
  const list = Object.values(st.movs).sort(function (a, b) {
    const da = a.m + String(a.d || 0).padStart(2, '0');
    const db = b.m + String(b.d || 0).padStart(2, '0');
    return da < db ? 1 : da > db ? -1 : 0;
  });
  const rows = [['Data', 'Tipo', 'Persona', 'Ambito', 'Categoria', 'Importo', 'Conto', 'Note', 'ID']];
  list.forEach(function (m) {
    rows.push([
      m.m + (m.d ? '-' + String(m.d).padStart(2, '0') : ''),
      m.t === 'i' ? 'Entrata' : 'Spesa',
      NOMI[m.w] || m.w,
      m.s === 'p' ? 'Personale' : 'Casa',
      m.c, m.a, nome[m.k] || '', m.n || '', m.id
    ]);
  });
  sh.clear();
  sh.getRange(1, 1, rows.length, 1).setNumberFormat('@');
  sh.getRange(1, 1, rows.length, rows[0].length).setValues(rows);
  sh.setFrozenRows(1);
}
