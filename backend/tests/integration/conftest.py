from pathlib import Path
import mongomock
import mongomock.gridfs
import pytest
from fastapi.testclient import TestClient
from app.main import app
from app.core.auth import require_investigator
from app.core.config import settings
from app.db.mongo import get_db
from app.graph.service import InMemoryGraph, get_graph
from app.services.osint.lookup import get_osint
from app.services.speech.transcriber import get_transcriber
from app.services.threat_intel.lookup import seed_defaults

mongomock.gridfs.enable_gridfs_integration()
settings.smtp_host = ""   # tests never send real email

class FakeOsint:
    def enrich(self, host):
        if "fresh" in host:
            return {"domain": host, "created": "2026-10-25", "age_days": 4, "registrar": "Test Registrar",
                    "resolves": True, "ips": ["203.0.113.5"], "has_mx": False}
        return {"domain": host, "created": "2015-01-01", "age_days": 4000, "registrar": "Test Registrar",
                "resolves": True, "ips": ["203.0.113.9"], "has_mx": True}

class FakeTranscriber:
    """In tests the 'audio' bytes are simply the spoken text; '|' separates 8-second segments."""
    def transcribe(self, path):
        return Path(path).read_bytes().decode("utf-8", "ignore").replace("|", " ")
    def segments(self, path):
        parts = Path(path).read_bytes().decode("utf-8", "ignore").split("|")
        return [(i * 8.0, i * 8.0 + 7.5, p.strip()) for i, p in enumerate(parts)]

@pytest.fixture
def mock_db():
    db = mongomock.MongoClient()["test_shield"]
    seed_defaults(db)
    graph = InMemoryGraph()
    app.dependency_overrides[get_db] = lambda: db
    app.dependency_overrides[get_graph] = lambda: graph
    app.dependency_overrides[get_osint] = lambda: FakeOsint()
    app.dependency_overrides[get_transcriber] = lambda: FakeTranscriber()
    yield db
    app.dependency_overrides.clear()

@pytest.fixture
def client(mock_db):
    app.dependency_overrides[require_investigator] = lambda: {"email": "t@example.com", "role": "investigator", "name": "Test"}
    return TestClient(app)

@pytest.fixture
def anon_client(mock_db):
    app.dependency_overrides.pop(require_investigator, None)
    return TestClient(app)
