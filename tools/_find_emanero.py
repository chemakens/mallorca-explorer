import firebase_admin
from firebase_admin import credentials, firestore
cred = credentials.Certificate("../serviceAccountKey.json")
firebase_admin.initialize_app(cred)
db = firestore.client()
for doc in db.collection("events").stream():
    data = doc.to_dict()
    if "emanero" in data.get("title","").lower():
        print(f"{doc.id} | {data.get('title')} | {data.get('price')}")
