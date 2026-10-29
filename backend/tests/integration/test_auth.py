import re
from datetime import datetime
from app.core.auth import hash_password

CODE = re.compile(r"DS-[A-Z0-9]{4}-[A-Z0-9]{4}")

def _h(tok):
    return {"Authorization": f"Bearer {tok}"}

def _last_code(db, email):
    m = db.outbox.find_one({"to": email}, sort=[("_id", -1)])
    return CODE.search(m["body"]).group(0)

def _login(c, email, code, password=""):
    return c.post("/auth/login", json={"email": email, "code": code, "password": password})

def _code_login(c, db, email, password=""):
    c.post("/auth/request-login-code", json={"email": email})
    return _login(c, email, _last_code(db, email), password)

def _admin_token(c, db):
    db.users.insert_one({"name": "Admin", "email": "admin@example.com",
                         "password_hash": hash_password("adminpass1"), "role": "admin"})
    return _code_login(c, db, "admin@example.com", "adminpass1").json()["access_token"]

def _invite(c, db, email="inv@example.com"):
    c.post("/auth/request-access", json={"name": "Inspector", "email": email, "organisation": "Cyber Cell"})
    tok = _admin_token(c, db)
    rid = next(r["id"] for r in c.get("/auth/requests", headers=_h(tok)).json() if r["email"] == email)
    c.post(f"/auth/requests/{rid}/approve", headers=_h(tok))
    return _last_code(db, email)

def test_first_login_with_approval_code_creates_account(anon_client, mock_db):
    r = _login(anon_client, "inv@example.com", _invite(anon_client, mock_db)).json()
    assert r["role"] == "investigator" and r["first_login"] is True and r["has_password"] is False
    assert anon_client.get("/stats", headers=_h(r["access_token"])).status_code == 200

def test_each_login_needs_a_fresh_code(anon_client, mock_db):
    first = _invite(anon_client, mock_db)
    _login(anon_client, "inv@example.com", first)
    anon_client.post("/auth/request-login-code", json={"email": "inv@example.com"})
    fresh = _last_code(mock_db, "inv@example.com")
    assert _login(anon_client, "inv@example.com", fresh).status_code == 200
    assert _login(anon_client, "inv@example.com", fresh).status_code == 401   # used up
    assert _login(anon_client, "inv@example.com", first).status_code == 401   # approval code is one-time

def test_password_becomes_second_factor(anon_client, mock_db):
    tok = _login(anon_client, "inv@example.com", _invite(anon_client, mock_db)).json()["access_token"]
    assert anon_client.post("/auth/set-password", json={"new_password": "strongpass1"}, headers=_h(tok)).status_code == 200
    anon_client.post("/auth/request-login-code", json={"email": "inv@example.com"})
    code = _last_code(mock_db, "inv@example.com")
    assert _login(anon_client, "inv@example.com", code).status_code == 401
    assert _login(anon_client, "inv@example.com", code, "strongpass1").status_code == 200

def test_login_code_expires(anon_client, mock_db):
    _login(anon_client, "inv@example.com", _invite(anon_client, mock_db))
    anon_client.post("/auth/request-login-code", json={"email": "inv@example.com"})
    code = _last_code(mock_db, "inv@example.com")
    mock_db.login_codes.update_many({}, {"$set": {"expires_at": datetime(2000, 1, 1)}})
    assert _login(anon_client, "inv@example.com", code).status_code == 401

def test_code_locks_after_five_wrong_tries(anon_client, mock_db):
    _login(anon_client, "inv@example.com", _invite(anon_client, mock_db))
    anon_client.post("/auth/request-login-code", json={"email": "inv@example.com"})
    code = _last_code(mock_db, "inv@example.com")
    for _ in range(5):
        _login(anon_client, "inv@example.com", "DS-AAAA-AAAA")
    assert _login(anon_client, "inv@example.com", code).status_code == 401

def test_code_request_does_not_reveal_accounts(anon_client, mock_db):
    r = anon_client.post("/auth/request-login-code", json={"email": "nobody@example.com"})
    assert r.status_code == 200 and mock_db.outbox.count_documents({"to": "nobody@example.com"}) == 0

def test_wrong_code_rejected(anon_client):
    assert _login(anon_client, "x@example.com", "DS-AAAA-AAAA").status_code == 401

def test_approval_code_only_for_its_email(anon_client, mock_db):
    code = _invite(anon_client, mock_db)
    assert _login(anon_client, "someone.else@example.com", code).status_code == 401

def test_codes_stored_only_as_hash(anon_client, mock_db):
    code = _invite(anon_client, mock_db)
    assert code not in str(mock_db.invites.find_one())

def test_only_admin_can_see_requests(anon_client, mock_db):
    tok = _login(anon_client, "inv@example.com", _invite(anon_client, mock_db)).json()["access_token"]
    assert anon_client.get("/auth/requests", headers=_h(tok)).status_code == 403

def test_dashboard_data_needs_login(anon_client):
    assert anon_client.get("/stats").status_code == 401

def test_normal_user_cannot_open_dashboard_data(anon_client, mock_db):
    anon_client.post("/auth/register", json={"name": "V", "email": "v@example.com", "password": "strongpass1"})
    tok = _code_login(anon_client, mock_db, "v@example.com", "strongpass1").json()["access_token"]
    assert anon_client.get("/stats", headers=_h(tok)).status_code == 403

def test_password_is_stored_hashed(anon_client, mock_db):
    anon_client.post("/auth/register", json={"name": "U", "email": "u@example.com", "password": "strongpass1"})
    stored = mock_db.users.find_one({"email": "u@example.com"})["password_hash"]
    assert "strongpass1" not in stored and stored.startswith("pbkdf2_sha256$")
