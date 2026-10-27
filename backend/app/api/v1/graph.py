from fastapi import APIRouter, Depends, HTTPException
from app.graph.service import get_graph

router = APIRouter(prefix="/graph", tags=["graph"])

@router.get("/neighbours")
def neighbours(type: str, value: str, graph=Depends(get_graph)):
    try:
        return graph.neighbours(type, value)
    except Exception as e:
        raise HTTPException(status_code=503, detail=f"Graph unavailable: {e}")

@router.get("/stats")
def stats(graph=Depends(get_graph)):
    try:
        return graph.stats()
    except Exception as e:
        raise HTTPException(status_code=503, detail=f"Graph unavailable: {e}")
