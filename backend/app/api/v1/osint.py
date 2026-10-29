from fastapi import APIRouter, Depends, HTTPException
from app.core.auth import require_investigator
from app.services.osint.lookup import get_osint

router = APIRouter(prefix="/osint", tags=["osint"])

@router.get("/domain", dependencies=[Depends(require_investigator)])
def domain_lookup(name: str, osint=Depends(get_osint)):
    try:
        return osint.enrich(name)
    except Exception as e:
        raise HTTPException(status_code=503, detail=f"OSINT lookup failed: {e}")
