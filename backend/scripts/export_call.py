"""Export a call recording for investigators: python3 scripts/export_call.py <call_id | latest>"""
import hashlib, io, sys, wave
from datetime import datetime, timezone
from pathlib import Path
import gridfs
from bson import ObjectId
from pymongo import MongoClient

db = MongoClient("mongodb://localhost:27017")["digital_shield"]
fs = gridfs.GridFS(db)
arg = sys.argv[1] if len(sys.argv) > 1 else "latest"
if arg == "latest":
    call = db.calls.find_one({"$or": [{"chunk_count": {"$gt": 0}}, {"recording": {"$exists": True}}]},
                             sort=[("started_at", -1)])
else:
    call = db.calls.find_one({"_id": ObjectId(arg)})
if not call:
    sys.exit("No recorded call found.")
cid = str(call["_id"])
out = Path.home() / "digital-shield" / "exports" / f"call_{cid}"
out.mkdir(parents=True, exist_ok=True)
sha = lambda b: hashlib.sha256(b).hexdigest()
lines = [f"Call {cid} from {call['number']}  started {call['started_at']}",
         f"Final risk: {call['live_risk']['score']} {call['live_risk']['band']}", ""]

if call.get("recording"):                                   # the phone's own recording: one file
    data = fs.get(call["recording"]["file_id"]).read()
    ok = sha(data) == call["recording"]["sha256"]
    (out / "phone_recording.audio").write_bytes(data)
    lines.append(f"phone_recording  sha256 {sha(data)}  {'INTACT' if ok else 'ALTERED'}")
    final = sha(data)
else:                                                       # live recording: 10-second WAV chunks
    chunks = list(db.audio_chunks.find({"call_id": cid}).sort("seq", 1))
    frames, params = [], None
    for c in chunks:
        data = fs.get(c["file_id"]).read()
        ok = sha(data) == c["sha256"]
        (out / f"chunk_{c['seq']:02d}.wav").write_bytes(data)
        lines.append(f"chunk {c['seq']:02d}  {'INTACT' if ok else 'ALTERED'}  sha256 {c['sha256']}")
        lines.append(f"          \"{c.get('transcript', '')}\"")
        with wave.open(io.BytesIO(data)) as w:
            params = params or w.getparams()
            frames.append(w.readframes(w.getnframes()))
    if params:
        with wave.open(str(out / "full_call.wav"), "wb") as w:
            w.setparams(params)
            w.writeframes(b"".join(frames))
    final = sha((out / "full_call.wav").read_bytes()) if params else None
    lines += ["", f"full_call.wav  sha256 {final}  (joined from {len(chunks)} chunks)"]

(out / "manifest.txt").write_text("\n".join(lines) + "\n")
db.custody_events.insert_one({"evidence_type": "recording", "evidence_id": cid, "case_id": None,
                              "action": "exported", "actor": "investigator (export script)",
                              "timestamp": datetime.now(timezone.utc), "sha256": final})
print("\n".join(lines)); print(f"\nSaved to {out}")
