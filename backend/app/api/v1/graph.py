from fastapi import APIRouter, Depends, HTTPException
from app.graph.service import get_graph
from app.core.auth import require_investigator

router = APIRouter(prefix="/graph", tags=["graph"])

@router.get("/neighbours", dependencies=[Depends(require_investigator)])
def neighbours(type: str, value: str, graph=Depends(get_graph)):
    try:
        return graph.neighbours(type, value)
    except Exception as e:
        raise HTTPException(status_code=503, detail=f"Graph unavailable: {e}")

@router.get("/stats", dependencies=[Depends(require_investigator)])
def stats(graph=Depends(get_graph)):
    try:
        return graph.stats()
    except Exception as e:
        raise HTTPException(status_code=503, detail=f"Graph unavailable: {e}")
