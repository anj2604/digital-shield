import { useEffect, useState } from "react";
import { get, post, fmt } from "../api";

export default function AccessRequests() {
    const [reqs, setReqs] = useState(null);
    const [outbox, setOutbox] = useState([]);
    const [msg, setMsg] = useState(null);
    const [busy, setBusy] = useState(null);

    const refresh = () => {
        get("/auth/requests").then(setReqs).catch((e) => setMsg(e.message));
        get("/auth/outbox").then(setOutbox).catch(() => {});
    };
    useEffect(() => { refresh(); }, []);

    const decide = async (r, action) => {
        setBusy(r.id); setMsg(null);
        try {
            const res = await post(`/auth/requests/${r.id}/${action}`, {});
            if (action === "approve") {
                setMsg(res.delivery === "sent"
                    ? `Approved. The first login code was emailed to ${r.email}.`
                    : `Approved. Dev mode: the email with the code is shown below and in the backend terminal.`);
            } else {
                setMsg(`Request from ${r.email} rejected.`);
            }
            refresh();
        } catch (e) {
            setMsg(e.message);
        }
        setBusy(null);
    };

    return (
        <>
            <h3 className="mb-1">Access requests</h3>
            <p className="text-muted">
                Verify each person (official email, unit) before approving. Approval emails them a personal,
                single-use first login code that expires in 72 hours.
            </p>
            {msg && <div className="alert alert-info py-2">{msg}</div>}

            {!reqs ? <p>Loading…</p> : reqs.length === 0 ? (
                <div className="text-muted mb-4">No pending requests.</div>
            ) : (
                <table className="table">
                    <thead>
                        <tr><th>Name</th><th>Email</th><th>Organisation</th><th>Reason</th><th>Requested</th><th></th></tr>
                    </thead>
                    <tbody>
                        {reqs.map((r) => (
                            <tr key={r.id}>
                                <td>{r.name}</td>
                                <td>{r.email}</td>
                                <td>{r.organisation || "-"}</td>
                                <td className="small">{r.reason || "-"}</td>
                                <td className="small">{fmt(r.requested_at)}</td>
                                <td className="text-nowrap">
                                    <button className="btn btn-sm btn-success me-1" disabled={busy === r.id}
                                        onClick={() => decide(r, "approve")}>Approve</button>
                                    <button className="btn btn-sm btn-outline-danger" disabled={busy === r.id}
                                        onClick={() => decide(r, "reject")}>Reject</button>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            )}

            {outbox.length > 0 && (
                <>
                    <h5 className="mt-4">Sent emails <span className="text-muted small">(dev mode shows the content)</span></h5>
                    {outbox.map((m, i) => (
                        <div key={i} className="card p-3 mb-2">
                            <div className="small text-muted">To {m.to} · {fmt(m.created_at)} · {m.mode}</div>
                            <strong>{m.subject}</strong>
                            <pre className="mb-0 mt-2 small" style={{ whiteSpace: "pre-wrap" }}>{m.body}</pre>
                        </div>
                    ))}
                </>
            )}
        </>
    );
}