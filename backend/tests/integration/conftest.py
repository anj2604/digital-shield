import mongomock
import pytest
from fastapi.testclient import TestClient
from app.main import app
from app.db.mongo import get_db

@pytest.fixture
def mock_db():
    db = mongomock.MongoClient()["test_shield"]
    app.dependency_overrides[get_db] = lambda: db
    yield db
    app.dependency_overrides.clear()

@pytest.fixture
def client(mock_db):
    return TestClient(app)
