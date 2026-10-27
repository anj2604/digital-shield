from contextlib import asynccontextmanager
from fastapi import FastAPI
from app.api.v1.sms import router as sms_router
from app.api.v1.calls import router as calls_router
from app.api.v1.screenshots import router as screenshots_router
from app.api.v1.evidence import router as evidence_router
from app.api.v1.threat_intel import router as threat_intel_router
from app.db.mongo import get_db
from app.services.threat_intel.lookup import seed_defaults

@asynccontextmanager
async def lifespan(app):
    try:
        seed_defaults(get_db())
    except Exception:
        pass   # server still starts if MongoDB is down
    yield

app = FastAPI(title="Digital Shield API", lifespan=lifespan)

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
app.include_router(screenshots_router)
app.include_router(evidence_router)
app.include_router(threat_intel_router)
