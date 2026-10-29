from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
import dns.resolver
import whois
from fastapi import Depends
from pymongo.database import Database
from app.db.mongo import get_db

_pool = ThreadPoolExecutor(max_workers=4)
SECOND_LEVEL = {"co", "gov", "org", "net", "ac", "edu", "nic", "res"}

def _now():
    return datetime.now(timezone.utc).replace(tzinfo=None)

def registrable(host: str) -> str:
    parts = host.lower().strip(".").split(".")
    if len(parts) >= 3 and parts[-2] in SECOND_LEVEL and len(parts[-1]) == 2:
        return ".".join(parts[-3:])          # e.g. sbi.co.in
    return ".".join(parts[-2:])               # e.g. example.com

def _whois(domain):
    try:
        w = whois.whois(domain)
        created = w.creation_date[0] if isinstance(w.creation_date, list) else w.creation_date
        if not isinstance(created, datetime):
            created = None
        elif created.tzinfo:
            created = created.astimezone(timezone.utc).replace(tzinfo=None)
        registrar = w.registrar if isinstance(w.registrar, str) else None
        return {"created": created.date().isoformat() if created else None,
                "age_days": (_now() - created).days if created else None,
                "registrar": registrar}
    except Exception:
        return {"created": None, "age_days": None, "registrar": None}

def _dns(domain):
    out = {"resolves": None, "ips": [], "has_mx": None}
    try:
        out["ips"] = [r.to_text() for r in dns.resolver.resolve(domain, "A", lifetime=3)][:5]
        out["resolves"] = True
    except dns.resolver.NXDOMAIN:
        out["resolves"] = False
    except Exception:
        pass
    try:
        dns.resolver.resolve(domain, "MX", lifetime=3)
        out["has_mx"] = True
    except (dns.resolver.NoAnswer, dns.resolver.NXDOMAIN):
        out["has_mx"] = False
    except Exception:
        pass
    return out

class OsintService:
    def __init__(self, db):
        self.db = db

    def enrich(self, host: str) -> dict:
        domain = registrable(host)
        cached = self.db.osint_cache.find_one({"domain": domain})
        if cached and _now() - cached["fetched_at"] < timedelta(hours=24):
            return cached["data"]
        fut_w, fut_d = _pool.submit(_whois, domain), _pool.submit(_dns, domain)
        try:
            data = {"domain": domain, **fut_w.result(timeout=8), **fut_d.result(timeout=8)}
        except Exception:
            data = {"domain": domain, "created": None, "age_days": None, "registrar": None,
                    "resolves": None, "ips": [], "has_mx": None}
        self.db.osint_cache.update_one({"domain": domain},
                                       {"$set": {"data": data, "fetched_at": _now()}}, upsert=True)
        return data

def get_osint(db: Database = Depends(get_db)) -> OsintService:
    return OsintService(db)
