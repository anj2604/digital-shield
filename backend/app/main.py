from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.api.v1.sms import router as sms_router
from app.api.v1.calls import router as calls_router
from app.api.v1.screenshots import router as screenshots_router
from app.api.v1.evidence import router as evidence_router
from app.api.v1.threat_intel import router as threat_intel_router
from app.api.v1.graph import router as graph_router
from app.api.v1.cases import router as cases_router
from app.api.v1.stats import router as stats_router
from app.api.v1.links import router as links_router
from app.db.mongo import get_db
from app.graph.service import get_graph
from app.services.threat_intel.lookup import seed_defaults

@asynccontextmanager
async def lifespan(app):
    try:
        seed_defaults(get_db())
    except Exception:
        pass
    try:
        get_graph().ensure_schema()
    except Exception:
        pass
    yield

app = FastAPI(title="Digital Shield API", lifespan=lifespan)

# Lets the investigator dashboard (React, port 5173) call this API
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173", "http://127.0.0.1:5173"],
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/health")
def health():
    try:
        get_db().command("ping")
        mongo = "ok"
    except Exception:
        mongo = "unreachable"
    return {"status": "ok", "mongo": mongo}

for r in (sms_router, calls_router, screenshots_router, evidence_router,
          threat_intel_router, graph_router, cases_router, stats_router, links_router):
    app.include_router(r)
