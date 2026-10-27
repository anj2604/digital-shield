import mongomock
import mongomock.gridfs
import pytest
from fastapi.testclient import TestClient
from app.main import app
from app.db.mongo import get_db
from app.graph.service import InMemoryGraph, get_graph
from app.services.threat_intel.lookup import seed_defaults

mongomock.gridfs.enable_gridfs_integration()

@pytest.fixture
def mock_db():
    db = mongomock.MongoClient()["test_shield"]
    seed_defaults(db)
    graph = InMemoryGraph()
    app.dependency_overrides[get_db] = lambda: db
    app.dependency_overrides[get_graph] = lambda: graph
    yield db
    app.dependency_overrides.clear()

@pytest.fixture
def client(mock_db):
    return TestClient(app)
