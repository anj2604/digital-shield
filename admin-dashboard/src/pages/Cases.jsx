import { useEffect, useState } from "react";
import { API, get, fmt, BAND_CLASS } from "../api";

export default function Cases({ onOpenGraph }) {
    const [cases, setCases] = useState(null);
    const [selected, setSelected] = useState(null);
    const [detail, setDetail] = useState(null);
    const [err, setErr] = useState(null);

    useEffect(() => { get("/cases/").then(setCases).catch((e) => setErr(e.message)); }, []);

    useEffect(() => {
        if (!selected) return;
        setDetail(null);
        get(`/cases/${selected}`).then(setDetail).catch((e) => setErr(e.message));
    }, [selected]);

    if (err) return <div className="alert alert-danger">{err}</div>;
    if (!cases) return <p>Loading cases…</p>;

    return (
        <>
            <h3 className="mb-3">Cases</h3>
            <div className="row g-3">
                <div className="col-4">
                    <div className="list-group">
                        {cases.length === 0 && <div className="text-muted">No cases yet. Create one from the app.</div>}
                        {cases.map((c) => (
                            <button key={c.id}
                                className={`list-group-item list-group-item-action ${selected === c.id ? "active" : ""}`}
                                onClick={() => setSelected(c.id)}>
                                <div className="d-flex justify-content-between">
                                    <strong>{c.title}</strong>
                                    <span className={`badge bg-${BAND_CLASS[c.risk_band]}`}>{c.risk_band}</span>
                                </div>
                                <small>{fmt(c.created_at)} · {c.evidence_count} item(s)</small>
                            </button>
                        ))}
                    </div>
                </div>
                <div className="col-8">
                    {!selected && <div className="text-muted">Select a case to see its evidence and timeline.</div>}
                    {selected && !detail && <p>Loading…</p>}
                    {detail && <CaseDetail c={detail} onOpenGraph={onOpenGraph} />}
                </div>
            </div>
        </>
    );
}

function CaseDetail({ c, onOpenGraph }) {
    const integrityClass = (i) => (i === "intact" ? "bg-success" : i === "ALTERED" ? "bg-danger" : "bg-secondary");
    return (
        <div className="card p-3">
            <div className="d-flex justify-content-between align-items-start">
                <div>
                    <h5 className="mb-1">{c.title}</h5>
                    <div className="text-muted small">Case {c.id} · {fmt(c.created_at)} · status {c.status}</div>
                    <span className={`badge bg-${BAND_CLASS[c.risk_band]} mt-2`}>{c.risk_band} {c.risk_score}/100</span>
                </div>
                <a className="btn btn-outline-primary btn-sm" href={`${API}/cases/${c.id}/report.pdf`} target="_blank" rel="noreferrer">
                    Open PDF report
                </a>
            </div>

            <h6 className="mt-4">Indicators of compromise <span className="text-muted small">(click to open in graph)</span></h6>
            <div>
                {c.ioc_values.map((v) => {
                    const [t, ...rest] = v.split(":");
                    const val = rest.join(":");
                    return (
                        <button key={v} className="btn btn-sm btn-outline-secondary me-2 mb-2" onClick={() => onOpenGraph(t, val)}>
                            {t}: {val}
                        </button>
                    );
                })}
            </div>

            <h6 className="mt-3">Evidence</h6>
            <table className="table table-sm">
                <thead><tr><th>Type</th><th>Time</th><th>Risk</th><th>Content</th><th>Integrity</th></tr></thead>
                <tbody>
                    {c.evidence.map((e) => (
                        <tr key={e.id}>
                            <td>{e.type}</td>
                            <td className="small">{fmt(e.time)}</td>
                            <td><span className={`badge bg-${BAND_CLASS[e.risk_band]}`}>{e.risk_band} {e.risk_score}</span></td>
                            <td className="small" style={{ maxWidth: 320 }}>{e.label.slice(0, 140)}</td>
                            <td><span className={`badge ${integrityClass(e.integrity)}`}>{e.integrity}</span></td>
                        </tr>
                    ))}
                </tbody>
            </table>

            <h6 className="mt-3">Chain of custody</h6>
            <ul className="list-group list-group-flush small">
                {c.timeline.map((t, i) => (
                    <li key={i} className="list-group-item">
                        {fmt(t.timestamp)} — <strong>{t.action}</strong> · {t.evidence_type} {String(t.evidence_id).slice(-8)} · {t.actor}
                    </li>
                ))}
            </ul>
        </div>
    );
}