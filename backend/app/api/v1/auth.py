import hashlib
import hmac
import re
import secrets
from datetime import datetime, timedelta, timezone
from bson import ObjectId
from bson.errors import InvalidId
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from pymongo.database import Database
from app.core.auth import create_token, current_user, hash_password, require_admin, verify_password
from app.core.config import settings
from app.db.mongo import get_db
from app.services.mail import send_email

router = APIRouter(prefix="/auth", tags=["auth"])

ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"   # no 0/O or 1/I confusion
LOGIN_CODE_MINUTES = 10
MAX_ATTEMPTS = 5
GENERIC = ("If this email belongs to an approved investigator, a login code has been sent. "
           f"It is valid for {LOGIN_CODE_MINUTES} minutes.")
BAD_CODE = "Invalid, expired or already used code"

# ---------- models ----------
class AccessRequestIn(BaseModel):
    name: str
    email: str
    organisation: str = ""
    reason: str = ""

class LoginCodeIn(BaseModel):
    email: str

class LoginIn(BaseModel):
    email: str
    code: str
    password: str = ""

class SetPasswordIn(BaseModel):
    new_password: str
    current_password: str = ""

class RegisterIn(BaseModel):          # plain app users only (not investigators)
    name: str
    email: str
    password: str

# ---------- helpers ----------
def _now():
    return datetime.now(timezone.utc).replace(tzinfo=None)

def _email(e):
    return e.strip().lower()

def _valid_email(e):
    return re.fullmatch(r"[^@\s]+@[^@\s]+\.[^@\s]+", e) is not None

def _oid(v):
    try:
        return ObjectId(v)
    except InvalidId:
        raise HTTPException(status_code=404, detail="Request not found")

def new_code():
    raw = "".join(secrets.choice(ALPHABET) for _ in range(8))
    return f"DS-{raw[:4]}-{raw[4:]}"

def code_hash(code: str) -> str:
    return hashlib.sha256(code.strip().upper().encode()).hexdigest()

def _token_response(u, first=False):
    return {"access_token": create_token(u["email"], u["role"], u["name"]), "token_type": "bearer",
            "role": u["role"], "name": u["name"], "first_login": first,
            "has_password": bool(u.get("password_hash"))}

# ---------- access requests (admin approves, first code is emailed) ----------
@router.post("/request-access", status_code=202)
def request_access(p: AccessRequestIn, db: Database = Depends(get_db)):
    email = _email(p.email)
    if not _valid_email(email) or not p.name.strip():
        raise HTTPException(status_code=400, detail="Please enter your name and a valid email")
    if db.users.find_one({"email": email}):
        raise HTTPException(status_code=409, detail="This email already has an account")
    db.access_requests.update_one(
        {"email": email, "status": "pending"},
        {"$set": {"name": p.name.strip(), "organisation": p.organisation.strip(),
                  "reason": p.reason.strip(), "requested_at": _now()}},
        upsert=True,
    )
    return {"status": "pending",
            "message": "Request received. Once your identity is verified, your first login code will be emailed to you."}

@router.get("/requests", dependencies=[Depends(require_admin)])
def list_requests(status: str = "pending", db: Database = Depends(get_db)):
    docs = db.access_requests.find({"status": status}).sort("requested_at", -1)
    return [{**{k: v for k, v in d.items() if k != "_id"}, "id": str(d["_id"])} for d in docs]

@router.post("/requests/{req_id}/approve")
def approve(req_id: str, admin: dict = Depends(require_admin), db: Database = Depends(get_db)):
    req = db.access_requests.find_one({"_id": _oid(req_id), "status": "pending"})
    if not req:
        raise HTTPException(status_code=404, detail="Pending request not found")
    code = new_code()
    db.invites.update_many({"email": req["email"], "used": False}, {"$set": {"used": True, "revoked": True}})
    db.invites.insert_one({"email": req["email"], "code_hash": code_hash(code), "used": False,
                           "expires_at": _now() + timedelta(hours=settings.invite_hours),
                           "created_by": admin["email"], "created_at": _now()})
    db.access_requests.update_one({"_id": req["_id"]},
                                  {"$set": {"status": "approved", "decided_by": admin["email"], "decided_at": _now()}})
    body = (f"Hello {req['name']},\n\n"
            f"Your request for investigator access to Digital Shield has been verified and approved.\n\n"
            f"Your first login code:  {code}\n\n"
            f"Open the dashboard, enter this email ({req['email']}) and the code. A password is optional.\n"
            f"The code works once, only for this email, and expires in {settings.invite_hours} hours.\n"
            f"After that, every login uses a fresh code sent to this email.\n\n"
            f"If you did not request access, you can ignore this email.\n\nDigital Shield")
    delivery = send_email(db, req["email"], "Your Digital Shield access is approved", body)
    return {"status": "approved", "email": req["email"], "delivery": delivery}

@router.post("/requests/{req_id}/reject")
def reject(req_id: str, admin: dict = Depends(require_admin), db: Database = Depends(get_db)):
    res = db.access_requests.update_one({"_id": _oid(req_id), "status": "pending"},
                                        {"$set": {"status": "rejected", "decided_by": admin["email"], "decided_at": _now()}})
    if res.matched_count == 0:
        raise HTTPException(status_code=404, detail="Pending request not found")
    return {"status": "rejected"}

@router.get("/outbox", dependencies=[Depends(require_admin)])
def outbox(limit: int = 20, db: Database = Depends(get_db)):
    return list(db.outbox.find({}, {"_id": 0}).sort("created_at", -1).limit(limit))

# ---------- login: emailed code (mandatory) + password (optional second factor) ----------
@router.post("/request-login-code")
def request_login_code(p: LoginCodeIn, db: Database = Depends(get_db)):
    email = _email(p.email)
    if not db.users.find_one({"email": email}):
        return {"message": GENERIC}        # same answer, so nobody can probe which emails exist
    if db.login_codes.find_one({"email": email, "created_at": {"$gt": _now() - timedelta(seconds=60)}}):
        return {"message": GENERIC}        # at most one code per minute
    code = new_code()
    db.login_codes.update_many({"email": email, "used": False}, {"$set": {"used": True}})
    db.login_codes.insert_one({"email": email, "code_hash": code_hash(code), "used": False, "attempts": 0,
                               "expires_at": _now() + timedelta(minutes=LOGIN_CODE_MINUTES),
                               "created_at": _now()})
    send_email(db, email, "Your Digital Shield login code",
               f"Your login code:  {code}\n\nIt is valid for {LOGIN_CODE_MINUTES} minutes and works once.\n"
               f"If you did not try to log in, you can ignore this email.\n\nDigital Shield")
    return {"message": GENERIC}

@router.post("/login")
def login(p: LoginIn, db: Database = Depends(get_db)):
    email, h = _email(p.email), code_hash(p.code)
    user = db.users.find_one({"email": email})

    # First login: the code from the approval email creates the account
    if user is None:
        inv = db.invites.find_one({"email": email, "code_hash": h, "used": False})
        if not inv or inv["expires_at"] < _now():
            raise HTTPException(status_code=401, detail=BAD_CODE)
        if p.password and len(p.password) < 8:
            raise HTTPException(status_code=400, detail="Password must be at least 8 characters, or leave it empty")
        req = db.access_requests.find_one({"email": email, "status": "approved"}, sort=[("decided_at", -1)])
        user = {"name": req["name"] if req else email.split("@")[0], "email": email, "role": "investigator",
                "password_hash": hash_password(p.password) if p.password else None, "created_at": _now()}
        db.users.insert_one(user)
        db.invites.update_one({"_id": inv["_id"]}, {"$set": {"used": True, "used_at": _now()}})
        return _token_response(user, first=True)

    # Every later login: the latest emailed code
    lc = db.login_codes.find_one({"email": email, "used": False}, sort=[("created_at", -1)])
    if not lc or lc["expires_at"] < _now():
        raise HTTPException(status_code=401, detail=BAD_CODE)

    def fail(detail):
        attempts = lc.get("attempts", 0) + 1
        db.login_codes.update_one({"_id": lc["_id"]},
                                  {"$set": {"attempts": attempts, "used": attempts >= MAX_ATTEMPTS}})
        raise HTTPException(status_code=401, detail=detail)

    if not hmac.compare_digest(lc["code_hash"], h):
        fail(BAD_CODE)
    if user.get("password_hash") and not (p.password and verify_password(p.password, user["password_hash"])):
        fail("This account has a password: enter it together with the code")
    db.login_codes.update_one({"_id": lc["_id"]}, {"$set": {"used": True, "used_at": _now()}})
    return _token_response(user)

# ---------- account ----------
@router.get("/me")
def me(user: dict = Depends(current_user), db: Database = Depends(get_db)):
    u = db.users.find_one({"email": user["email"]}) or {}
    return {**user, "has_password": bool(u.get("password_hash"))}

@router.post("/set-password")
def set_password(p: SetPasswordIn, user: dict = Depends(current_user), db: Database = Depends(get_db)):
    u = db.users.find_one({"email": user["email"]})
    if not u:
        raise HTTPException(status_code=404, detail="Account not found")
    if len(p.new_password) < 8:
        raise HTTPException(status_code=400, detail="Password must be at least 8 characters")
    if u.get("password_hash") and not verify_password(p.current_password, u["password_hash"]):
        raise HTTPException(status_code=403, detail="Current password is wrong")
    db.users.update_one({"_id": u["_id"]}, {"$set": {"password_hash": hash_password(p.new_password)}})
    return {"status": "ok", "has_password": True}

@router.post("/register", status_code=201)
def register(p: RegisterIn, db: Database = Depends(get_db)):
    """Plain app users only. Investigators get accounts through approval."""
    email = _email(p.email)
    if not _valid_email(email) or len(p.password) < 8:
        raise HTTPException(status_code=400, detail="A valid email and a password of at least 8 characters are required")
    if db.users.find_one({"email": email}):
        raise HTTPException(status_code=409, detail="Email already registered")
    db.users.insert_one({"name": p.name.strip(), "email": email, "password_hash": hash_password(p.password),
                         "role": "user", "created_at": _now()})
    return {"email": email, "role": "user", "name": p.name.strip()}
