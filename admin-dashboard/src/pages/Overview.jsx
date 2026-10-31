import { useEffect, useRef, useState } from "react";
import { get, fmt, BAND_CLASS } from "../api";

const BANDS = ["CRITICAL", "HIGH", "MEDIUM", "LOW"];
const KIND_ICON = { sms: "💬", call: "📞", screenshot: "🖼", file: "📁" };

function ago(t) {
    const d = new Date(/Z$|[+-]\d\d:\d\d$/.test(t) ? t : t + "Z");
    const s = Math.max(0, Math.round((Date.now() - d.getTime()) / 1000));
    if (s < 60) return `${s}s ago`;
    if (s < 3600) return `${Math.floor(s / 60)}m ago`;
    if (s < 86400) return `${Math.floor(s / 3600)}h ago`;
    return d.toLocaleDateString();
}

function TrendChart({ days }) {
    const totals = days.map((d) => BANDS.reduce((a, b) => a + (d[b] || 0), 0));
    const max = Math.max(1, ...totals);
    return (
        <div className="d-flex align-items-end gap-2" style={{ height: 190 }}>
            {days.map((d, i) => (
                <div key={d.day} className="flex-fill d-flex flex-column justify-content-end align-items-center" style={{ height: "100%" }}>
                    <div className="small text-muted mb-1">{totals[i] || ""}</div>
                    <div className="w-100 d-flex flex-column-reverse rounded overflow-hidden"
                        style={{ height: `${(totals[i] / max) * 140}px`, minHeight: totals[i] ? 4 : 0 }}>
                        {[...BANDS].reverse().map((b) =>
                            d[b] ? <div key={b} className={`bg-${BAND_CLASS[b]}`} style={{ height: `${(d[b] / totals[i]) * 100}%` }} title={`${b}: ${d[b]}`} /> : null
                        )}
                    </div>
                    <div className="small text-muted mt-1">{new Date(d.day).toLocaleDateString(undefined, { weekday: "short" })}</div>
                </div>
            ))}
        </div>
    );
}

export default function Overview() {
    const [s, setS] = useState(null);
    const [cases, setCases] = useState([]);
    const [feed, setFeed] = useState([]);
    const [trends, setTrends] = useState(null);
    const [err, setErr] = useState(null);
    const [flash, setFlash] = useState(null);
    const lastTotal = useRef(null);

    useEffect(() => {
        let alive = true;
        const load = () =>
            Promise.all([get("/stats"), get("/cases/"), get("/activity?limit=8"), get("/stats/trends?days=7")])
                .then(([st, cs, ac, tr]) => {
                    if (!alive) return;
                    const total = st.sms + st.calls + st.screenshots + (st.files || 0);
                    if (lastTotal.current !== null && total > lastTotal.current) {
                        setFlash(`${total - lastTotal.current} new item(s) just arrived`);
                        setTimeout(() => setFlash(null), 4000);
                    }
                    lastTotal.current = total;
                    setS(st); setCases(cs.slice(0, 5)); setFeed(ac); setTrends(tr); setErr(null);
                })
                .catch((e) => alive && setErr(e.message));
            load();
            const id = setInterval(load, 5000);       // live: refresh every 5 seconds
            return () => { alive = false; clearInterval(id); };
        }, []);

        if (err && !s) return <div className="alert alert-danger">Backend not reachable: {err}</div>;
        if (!s || !trends) return <p>Loading…</p>;

        const total = Object.values(s.bands).reduce((a, b) => a + b, 0) || 1;
        const maxInd = Math.max(1, ...trends.top_indicators.map((i) => i.count));
        const cards = [["Messages", s.sms], ["Calls", s.calls], ["Screenshots", s.screenshots],
                    ["Files", s.files || 0], ["Cases", s.cases], ["Reported IOCs", s.iocs]];

    return (
        <>
            <div className="d-flex align-items-center gap-3 mb-3">
                <h3 className="mb-0">Overview</h3>
                <span className="badge bg-danger-subtle text-danger-emphasis d-flex align-items-center gap-1">
                    <span className="spinner-grow spinner-grow-sm" style={{ width: 8, height: 8 }} /> LIVE
                </span>
            </div>
            {flash && <div className="alert alert-success py-2">🔔 {flash}</div>}

            <div className="row g-3 mb-4">
                {cards.map(([label, value]) => (
                    <div className="col-6 col-md-4 col-xl-2" key={label}>
                        <div className="card p-3 h-100">
                            <div className="text-muted small">{label}</div>
                            <div className="fs-3 fw-bold">{value}</div>
                        </div>
                    </div>
                ))}
            </div>

            <div className="row g-3 mb-4">
                <div className="col-lg-7">
                    <div className="card p-3 h-100">
                        <h6>Alerts in the last 7 days</h6>
                        <TrendChart days={trends.days} />
                        <div className="small mt-2 d-flex gap-3 flex-wrap">
                            {BANDS.map((b) => <span key={b}><span className={`badge bg-${BAND_CLASS[b]} me-1`}>&nbsp;</span>{b}</span>)}
                        </div>
                    </div>
                </div>
                <div className="col-lg-5">
                    <div className="card p-3 h-100">
                        <h6>Live activity</h6>
                        {feed.length === 0 ? <div className="text-muted">Nothing yet.</div> : (
                            <ul className="list-group list-group-flush">
                                {feed.map((f, i) => (
                                    <li key={i} className="list-group-item d-flex align-items-start gap-2 px-0">
                                        <span>{KIND_ICON[f.kind]}</span>
                                        <div className="flex-grow-1 small" style={{ minWidth: 0 }}>
                                            <div className="text-truncate">{f.label}</div>
                                            <div className="text-muted">{ago(f.time)}</div>
                                        </div>
                                        <span className={`badge bg-${BAND_CLASS[f.band]}`}>{f.band}</span>
                                    </li>
                                ))}
                            </ul>
                        )}
                    </div>
                </div>
            </div>

            <div className="row g-3 mb-4">
                <div className="col-lg-4">
                    <div className="card p-3 h-100">
                        <h6>Most common scam tactics</h6>
                        {trends.top_indicators.length === 0 && <div className="text-muted small">No data yet.</div>}
                        {trends.top_indicators.map((i) => (
                            <div key={i.indicator} className="mb-2">
                                <div className="d-flex justify-content-between small">
                                    <span>{i.indicator.replaceAll("_", " ")}</span><span>{i.count}</span>
                                </div>
                                <div className="progress" style={{ height: 8 }}>
                                    <div className="progress-bar" style={{ width: `${(100 * i.count) / maxInd}%` }} />
                                </div>
                            </div>
                        ))}
                    </div>
                </div>
                <div className="col-lg-4">
                    <div className="card p-3 h-100">
                        <h6>Most reported indicators</h6>
                        <table className="table table-sm mb-0">
                            <tbody>
                                {trends.top_iocs.map((x) => (
                                    <tr key={`${x.type}:${x.value}`}>
                                        <td className="small text-muted">{x.type}</td>
                                        <td className="small text-truncate" style={{ maxWidth: 160 }}>{x.value}</td>
                                        <td className="small text-end">{x.report_count}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>
                <div className="col-lg-4">
                    <div className="card p-3 h-100">
                        <h6>Risk distribution</h6>
                        {BANDS.map((b) => (
                            <div key={b} className="d-flex align-items-center mb-2">
                                <span className="small" style={{ width: 80 }}>{b}</span>
                                <div className="progress flex-grow-1" style={{ height: 16 }}>
                                    <div className={`progress-bar bg-${BAND_CLASS[b]}`} style={{ width: `${(100 * s.bands[b]) / total}%` }}>
                                        {s.bands[b] || ""}
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>
                </div>
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