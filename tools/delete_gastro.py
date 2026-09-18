#!/usr/bin/env python3
import warnings
warnings.filterwarnings('ignore')
import firebase_admin
from firebase_admin import credentials, firestore

KEY = '/Users/usuario/Code/mallorca-explorer/serviceAccountKey.json'
cred = credentials.Certificate(KEY)
firebase_admin.initialize_app(cred)
db = firestore.client()

ids = [
    'web-mallorca.com_gastro-433a34002166','web-mallorca.com_gastro-a43e76f4b7a5',
    'web-mallorca.com_gastro-e2b2dc257fed','web-mallorca.com_gastro-4a9214658995',
    'web-mallorca.com_gastro-95e1c5009531','web-mallorca.com_gastro-5c7902f0ebb7',
    'web-mallorca.com_gastro-af60aaa56aef','web-mallorca.com_gastro-d258eddf2767',
    'web-mallorca.com_gastro-1a781836dc86','web-mallorca.com_gastro-f5708fbfa38f',
    'web-mallorca.com_gastro-0705638e94cc','web-mallorca.com_gastro-5ac251f7e8ac',
    'web-mallorca.com_gastro-27a6c3bb972f','web-mallorca.com_gastro-748c32478395',
    'web-mallorca.com_gastro-6687fdb0211c','web-mallorca.com_gastro-f80fa7f6d577',
    'web-mallorca.com_gastro-14f9094210a2','web-mallorca.com_gastro-b7e80d37aae9',
    'web-mallorca.com_gastro-40b477a66293','web-mallorca.com_gastro-150d8cb2f14a',
    'web-mallorca.com_gastro-722da25f2725','web-mallorca.com_gastro-00cf2adc368a',
    'web-mallorca.com_gastro-b3cc79b3f87a','web-mallorca.com_gastro-3bb844569955',
    'web-mallorca.com_gastro-95e64c324940','web-mallorca.com_gastro-c39dc8aeb72d',
    'web-mallorca.com_gastro-f32e2871aabd','web-mallorca.com_gastro-d683c37db938',
    'web-mallorca.com_gastro-29cd1142720d','web-mallorca.com_gastro-9acddc6b189f',
    'web-mallorca.com_gastro-c3c196a53ed5',
]

batch = db.batch()
for doc_id in ids:
    batch.delete(db.collection('events').document(doc_id))
batch.commit()
print(f'Eliminados {len(ids)} eventos gastro de Firestore')
