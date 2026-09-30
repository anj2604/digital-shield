from fastapi import FastAPI
from app.api.v1.sms import router as sms_router
from app.api.v1.calls import router as calls_router

app = FastAPI(title="Digital Shield API")

@app.get("/health")
def health():
    return {"status": "ok"}

app.include_router(sms_router)
app.include_router(calls_router)
