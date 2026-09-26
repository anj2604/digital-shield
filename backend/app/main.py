from fastapi import FastAPI

app = FastAPI(title="Digital Shield API")

@app.get("/health")
def health():
    return {"status": "ok"}
