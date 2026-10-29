import base64
import hashlib
import hmac
import os
from datetime import datetime, timedelta, timezone
from typing import Optional
import jwt
from fastapi import Depends, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from app.core.config import settings

ITERATIONS = 200_000
bearer = HTTPBearer(auto_error=False)

def hash_password(password: str) -> str:
    salt = os.urandom(16)
    dk = hashlib.pbkdf2_hmac("sha256", password.encode(), salt, ITERATIONS)
    return f"pbkdf2_sha256${ITERATIONS}${base64.b64encode(salt).decode()}${base64.b64encode(dk).decode()}"

def verify_password(password: str, stored: str) -> bool:
    try:
        _, iterations, salt_b64, hash_b64 = stored.split("$")
    except ValueError:
        return False
    dk = hashlib.pbkdf2_hmac("sha256", password.encode(), base64.b64decode(salt_b64), int(iterations))
    return hmac.compare_digest(dk, base64.b64decode(hash_b64))

def create_token(email: str, role: str, name: str) -> str:
    payload = {"sub": email, "role": role, "name": name,
               "exp": datetime.now(timezone.utc) + timedelta(hours=settings.token_hours)}
    return jwt.encode(payload, settings.secret_key, algorithm="HS256")

def current_user(creds: Optional[HTTPAuthorizationCredentials] = Depends(bearer)) -> dict:
    if creds is None:
        raise HTTPException(status_code=401, detail="Login required")
    try:
        data = jwt.decode(creds.credentials, settings.secret_key, algorithms=["HS256"])
    except jwt.ExpiredSignatureError:
        raise HTTPException(status_code=401, detail="Session expired, please log in again")
    except jwt.InvalidTokenError:
        raise HTTPException(status_code=401, detail="Invalid token")
    return {"email": data["sub"], "role": data.get("role"), "name": data.get("name")}

def require_investigator(user: dict = Depends(current_user)) -> dict:
    if user["role"] not in ("investigator", "admin"):
        raise HTTPException(status_code=403, detail="Investigator access only")
    return user


def require_admin(user: dict = Depends(current_user)) -> dict:
    if user["role"] != "admin":
        raise HTTPException(status_code=403, detail="Admin access only")
    return user
