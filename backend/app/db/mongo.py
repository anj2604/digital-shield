from pymongo import MongoClient
from pymongo.database import Database
from app.core.config import settings

client = MongoClient(settings.mongo_uri, serverSelectionTimeoutMS=3000)

def get_db() -> Database:
    return client[settings.mongo_db]
