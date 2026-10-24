from fastapi import FastAPI
from app.api.v1.sms import router as sms_router
from app.api.v1.calls import router as calls_router
from app.db.mongo import get_db

app = FastAPI(title="Digital Shield API")

@app.get("/health")
def health():
    try:
        get_db().command("ping")
        mongo = "ok"
    except Exception:
        mongo = "unreachable"
    return {"status": "ok", "mongo": mongo}

app.include_router(sms_router)
app.include_router(calls_router)
