import { useState } from "react";
import { get } from "../api";

export default function Osint() {
    const [name, setName] = useState("");
    const [data, setData] = useState(null);
    const [err, setErr] = useState(null);
    const [busy, setBusy] = useState(false);

    const run = async (e) => {
        e.preventDefault();
        setBusy(true); setErr(null); setData(null);
        try {
            setData(await get(`/osint/domain?name=${encodeURIComponent(name.trim())}`));
        } catch (x) {
            setErr(x.message);
        }
        setBusy(false);
    };

    const yesNo = (v) => (v === true ? "Yes" : v === false ? "No" : "Unknown");

    return (
        <>
            <h3 className="mb-1">OSINT: domain lookup</h3>
            <p className="text-muted">
                Public facts about a website. These add warning signs; they never prove a site is malicious on their own.
            </p>
            <form className="d-flex gap-2 mb-3" onSubmit={run}>
                <input className="form-control" placeholder="e.g. example.com" value={name}
                    onChange={(e) => setName(e.target.value)} />
                <button className="btn btn-primary" disabled={!name.trim() || busy}>{busy ? "Looking up…" : "Look up"}</button>
            </form>
            {err && <div className="alert alert-danger">{err}</div>}
            {data && (
                <table className="table" style={{ maxWidth: 640 }}>
                    <tbody>
                        <tr><th>Domain</th><td>{data.domain}</td></tr>
                        <tr><th>Registered on</th><td>{data.created || "Unknown"}</td></tr>
                        <tr><th>Age</th><td>
                            {data.age_days == null ? "Unknown" : `${data.age_days} days`}{" "}
                            {data.age_days != null && data.age_days < 90 && <span className="badge bg-warning text-dark">new domain</span>}
                        </td></tr>
                        <tr><th>Registrar</th><td>{data.registrar || "Unknown"}</td></tr>
                        <tr><th>Exists in DNS</th><td>{yesNo(data.resolves)}</td></tr>
                        <tr><th>IP addresses</th><td>{(data.ips || []).join(", ") || "-"}</td></tr>
                        <tr><th>Mail server (MX)</th><td>{yesNo(data.has_mx)}</td></tr>
                    </tbody>
                </table>
            )}
        </>
    );
}