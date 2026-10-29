import { useEffect, useState } from "react";
import { get } from "../api";

const W = 900, H = 560;
const COLORS = { center: "#dc3545", ioc: "#20c997", evidence: "#fd7e14" };

function layout(g) {
    const cx = W / 2, cy = H / 2;
    const pos = { [g.center]: { x: cx, y: cy } };
    const ev = g.nodes.filter((n) => n.kind === "evidence");
    const io = g.nodes.filter((n) => n.kind === "ioc" && n.id !== g.center);
    ev.forEach((n, i) => {
        const a = (2 * Math.PI * i) / Math.max(ev.length, 1) - Math.PI / 2;
        pos[n.id] = { x: cx + 160 * Math.cos(a), y: cy + 160 * Math.sin(a) };
    });
    io.forEach((n, i) => {
        const a = (2 * Math.PI * i) / Math.max(io.length, 1) - Math.PI / 2 + 0.3;
        pos[n.id] = { x: cx + 260 * Math.cos(a), y: cy + 225 * Math.sin(a) };
    });
    return pos;
}

export default function GraphView({ initial }) {
    const [type, setType] = useState(initial?.type || "phone");
    const [value, setValue] = useState(initial?.value || "");
    const [g, setG] = useState(null);
    const [err, setErr] = useState(null);

    const load = (t, v) => {
        setErr(null);
        get(`/graph/neighbours?type=${encodeURIComponent(t)}&value=${encodeURIComponent(v)}`)
            .then(setG)
            .catch((e) => setErr(e.message));
    };

    useEffect(() => {
        if (initial?.value) load(initial.type, initial.value);
    }, [initial]);

    const pos = g ? layout(g) : {};

    return (
        <>
            <h3 className="mb-3">Threat graph</h3>
            <form className="d-flex gap-2 mb-3" onSubmit={(e) => { e.preventDefault(); load(type, value); }}>
                <select className="form-select" style={{ width: 140 }} value={type} onChange={(e) => setType(e.target.value)}>
                    {["phone", "upi", "email", "url"].map((t) => <option key={t}>{t}</option>)}
                </select>
                <input className="form-control" placeholder="e.g. 9666666666 or fraud@upi"
                    value={value} onChange={(e) => setValue(e.target.value)} />
                <button className="btn btn-primary">Search</button>
            </form>

            {err && <div className="alert alert-danger">{err}</div>}

            {g && (
                <>
                    <div className="mb-2 text-muted">
                        {g.summary.evidence_count} evidence item(s), {g.summary.linked_iocs} linked indicator(s). Click a green node to explore from it.
                    </div>
                    <svg width="100%" viewBox={`0 0 ${W} ${H}`} className="bg-body border rounded">
                        {g.edges.map((e, i) =>
                            pos[e.source] && pos[e.target] ? (
                                <line key={i} x1={pos[e.source].x} y1={pos[e.source].y} x2={pos[e.target].x} y2={pos[e.target].y}
                                    stroke="#adb5bd" strokeWidth="2" />
                            ) : null
                        )}
                        {g.nodes.map((n) => {
                            const p = pos[n.id];
                            if (!p) return null;
                            const isCenter = n.id === g.center;
                            const color = isCenter ? COLORS.center : COLORS[n.kind];
                            const label = n.kind === "evidence" ? `${n.type} · ${n.band}` : `${n.type}: ${n.value}`;
                            return (
                                <g key={n.id} style={{ cursor: n.kind === "ioc" ? "pointer" : "default" }}
                                    onClick={() => { if (n.kind === "ioc") { setType(n.type); setValue(n.value); load(n.type, n.value); } }}>
                                    <circle cx={p.x} cy={p.y} r={isCenter ? 26 : 20} fill={color} />
                                    <text x={p.x} y={p.y + (isCenter ? 44 : 36)} textAnchor="middle" fontSize="12" fill="currentColor">                                        {label.length > 28 ? label.slice(0, 27) + "…" : label}
                                    </text>
                                </g>
                            );
                        })}
                    </svg>
                    <div className="small mt-2">
                        <span style={{ color: COLORS.center }}>●</span> searched indicator&nbsp;&nbsp;
                        <span style={{ color: COLORS.ioc }}>●</span> linked indicator&nbsp;&nbsp;
                        <span style={{ color: COLORS.evidence }}>●</span> evidence
                    </div>
                </>
            )}
        </>
    );
}