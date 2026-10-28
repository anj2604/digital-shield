import { useEffect, useState } from "react";
import { get, fmt, BAND_CLASS } from "../api";

export default function Overview() {
    const [s, setS] = useState(null);
    const [cases, setCases] = useState([]);
    const [err, setErr] = useState(null);

    useEffect(() => {
        Promise.all([get("/stats"), get("/cases/")])
            .then(([st, cs]) => { setS(st); setCases(cs.slice(0, 5)); })
            .catch((e) => setErr(e.message));
    }, []);

    if (err) return <div className="alert alert-danger">Backend not reachable: {err}</div>;
    if (!s) return <p>Loading…</p>;

    const total = Object.values(s.bands).reduce((a, b) => a + b, 0) || 1;
    const cards = [["Messages", s.sms], ["Calls", s.calls], ["Screenshots", s.screenshots],["Cases", s.cases], ["Reported IOCs", s.iocs]];

    return (
        <>
            <h3 className="mb-3">Overview</h3>
            <div className="row g-3 mb-4">
                {cards.map(([label, value]) => (
                    <div className="col" key={label}>
                        <div className="card p-3">
                            <div className="text-muted small">{label}</div>
                                <div className="fs-3 fw-bold">{value}</div>
                            </div>
                        </div>
                    ))}
                </div>

                <div className="card p-3 mb-4">
                    <h6>Risk distribution of stored evidence</h6>
                    {["CRITICAL", "HIGH", "MEDIUM", "LOW"].map((b) => (
                        <div key={b} className="d-flex align-items-center mb-2">
                            <span className="small" style={{ width: 90 }}>{b}</span>
                            <div className="progress flex-grow-1" style={{ height: 18 }}>
                                <div className={`progress-bar bg-${BAND_CLASS[b]}`} style={{ width: `${(100 * s.bands[b]) / total}%` }}>
                                    {s.bands[b] || ""}
                                </div>
                            </div>
                        </div>
                    ))}
                </div>
            

            <div className="card p-3">
                <h6>Recent cases</h6>
                {cases.length === 0 ? <div className="text-muted">No cases yet.</div> : (
                    <table className="table table-sm mb-0">
                        <thead><tr><th>Title</th><th>Created</th><th>Items</th><th>Risk</th></tr></thead>
                        <tbody>
                            {cases.map((c) => (
                                <tr key={c.id}>
                                    <td>{c.title}</td>
                                    <td className="small">{fmt(c.created_at)}</td>
                                    <td>{c.evidence_count}</td>
                                    <td><span className={`badge bg-${BAND_CLASS[c.risk_band]}`}>{c.risk_band} {c.risk_score}</span></td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                )}
            </div>
        </>
    );
}