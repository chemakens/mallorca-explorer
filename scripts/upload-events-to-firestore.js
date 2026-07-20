#!/usr/bin/env node
'use strict';

const fs = require('fs');
const path = require('path');
const admin = require('firebase-admin');

const PROJECT_ID = 'mallorca-explorer-49eb2';
const SEED_PATH = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'seed_data.json');
const COLLECTION = 'events';

function loadEvents() {
  const raw = fs.readFileSync(SEED_PATH, 'utf8');
  const seed = JSON.parse(raw);
  if (!Array.isArray(seed.events)) {
    throw new Error(`No se encontró un array "events" en ${SEED_PATH}`);
  }
  return seed.events;
}

// El "id" pasa a ser el document ID en Firestore, no un campo dentro del
// documento — así coincide con como lo lee la app (FirestoreEventDataSource
// usa `doc.id`, no un campo "id").
async function uploadEvents(events) {
  const db = admin.firestore();
  const total = events.length;
  let uploaded = 0;
  let failed = 0;

  for (const event of events) {
    const { id, ...fields } = event;
    const progress = `[${uploaded + failed + 1}/${total}]`;

    if (!id) {
      failed++;
      console.error(`${progress} ✗ evento sin "id", omitido: ${JSON.stringify(event).slice(0, 80)}...`);
      continue;
    }

    try {
      await db.collection(COLLECTION).doc(id).set(fields);
      uploaded++;
      console.log(`${progress} ✓ ${id}`);
    } catch (err) {
      failed++;
      console.error(`${progress} ✗ ${id}: ${err.message}`);
    }
  }

  console.log(`\n${uploaded}/${total} eventos subidos${failed > 0 ? `, ${failed} fallidos` : ''}.`);
  return failed === 0;
}

async function main() {
  admin.initializeApp({
    credential: admin.credential.applicationDefault(),
    projectId: PROJECT_ID,
  });

  const events = loadEvents();
  console.log(`Leídos ${events.length} eventos de ${path.relative(process.cwd(), SEED_PATH)}`);
  console.log(`Subiendo a Firestore — proyecto "${PROJECT_ID}", colección "${COLLECTION}"...\n`);

  const ok = await uploadEvents(events);
  process.exit(ok ? 0 : 1);
}

main().catch((err) => {
  console.error('Error fatal:', err);
  process.exit(1);
});
