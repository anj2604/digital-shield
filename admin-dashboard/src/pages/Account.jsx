import { useEffect, useState } from "react";
import { get, post } from "../api";

export default function Account() {
    const [me, setMe] = useState(null);
    const [current, setCurrent] = useState("");
    const [pw, setPw] = useState("");
    const [pw2, setPw2] = useState("");
    const [msg, setMsg] = useState(null);
    const [err, setErr] = useState(null);

    const load = () => get("/auth/me").then(setMe).catch((e) => setErr(e.message));
    useEffect(() => { load(); }, []);

    const save = async (e) => {
        e.preventDefault();
        setMsg(null); setErr(null);
        if (pw.length < 8) return setErr("Password must be at least 8 characters.");
        if (pw !== pw2) return setErr("The two passwords do not match.");
        try {
            await post("/auth/set-password", { new_password: pw, current_password: current });
            setMsg("Password saved. From now on, every login needs the emailed code AND this password.");
            setCurrent(""); setPw(""); setPw2("");
            load();
        } catch (x) {
            setErr(x.message);
        }
    };

    if (!me) return err ? <div className="alert alert-danger">{err}</div> : <p>Loading…</p>;

    return (
        <>
            <h3 className="mb-3">Account</h3>
            <div className="card p-3 mb-3" style={{ maxWidth: 560 }}>
                <strong>{me.name}</strong>
                <div className="text-muted small">{me.email} · {me.role}</div>
                <div className="mt-2">
                    Login security:{" "}
                    {me.has_password
                        ? <span className="badge bg-success">Email code + password (two-factor)</span>
                        : <span className="badge bg-warning text-dark">Email code only</span>}
                </div>
            </div>

            <form className="card p-3" style={{ maxWidth: 560 }} onSubmit={save}>
                <h6>{me.has_password ? "Change password" : "Add a password (recommended)"}</h6>
                <p className="small text-muted">
                    A password is optional. If you add one, every login needs both the emailed code and the password.
                </p>
                {me.has_password && (
                    <input type="password" className="form-control mb-2" placeholder="Current password"
                    value={current} onChange={(e) => setCurrent(e.target.value)} />
                )}
                <input type="password" className="form-control mb-2" placeholder="New password (8+ characters)"
                    value={pw} onChange={(e) => setPw(e.target.value)} />
                <input type="password" className="form-control mb-2" placeholder="Confirm new password"
                    value={pw2} onChange={(e) => setPw2(e.target.value)} />
                {err && <div className="alert alert-danger py-2 small">{err}</div>}
                {msg && <div className="alert alert-success py-2 small">{msg}</div>}
                <button className="btn btn-primary">Save password</button>
            </form>
        </>
    );
}