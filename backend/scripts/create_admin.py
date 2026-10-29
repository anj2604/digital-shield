import getpass
import sys
from datetime import datetime, timezone

sys.path.insert(0, ".")
from app.core.auth import hash_password   # noqa: E402
from app.db.mongo import get_db           # noqa: E402

email = input("Admin email: ").strip().lower()
name = input("Admin name: ").strip() or "Admin"
password = getpass.getpass("Password (8+ characters): ")
if len(password) < 8:
    sys.exit("Password too short.")
get_db().users.update_one(
    {"email": email},
    {"$set": {"name": name, "email": email, "password_hash": hash_password(password),
              "role": "admin", "created_at": datetime.now(timezone.utc)}},
    upsert=True,
)
print(f"Admin account ready: {email}")
