import { useEffect, useState } from "react";
import { get, post, fmt } from "../api";

export default function ThreatIntel({ onOpenGraph }) {
    const [rows, setRows] = useState(null);
    const [type, setType] = useState("phone");
    const [value, setValue] = useState("");
    const [msg, setMsg] = useState(null);

    const refresh = () => get("/threat-intel/iocs").then(setRows).catch((e) => setMsg(e.message));
    useEffect(() => { refresh(); }, []);

    const report = async (e) => {
        e.preventDefault();
        try 
        {
            await post("/threat-intel/report", { type, value });
            setValue("");
            setMsg("Reported.");
            refresh();
        } 
        catch (err) 
        {
            setMsg(err.message);
        }
    };

    return (
        <>
            <h3 className="mb-3">Threat intelligence</h3>
            <form className="d-flex gap-2 mb-2" onSubmit={report}>
                <select className="form-select" style={{ width: 140 }} value={type} onChange={(e) => setType(e.target.value)}>
                    {["phone", "upi", "email", "url", "domain"].map((t) => <option key={t}>{t}</option>)}
                </select>
                <input className="form-control" placeholder="Value to report" value={value}
                    onChange={(e) => setValue(e.target.value)} />
                <button className="btn btn-danger" disabled={!value.trim()}>Report</button>
            </form>
            {msg && <div className="small text-muted mb-3">{msg}</div>}

            {!rows ? <p>Loading…</p> : (
                <table className="table table-sm bg-white">
                    <thead><tr><th>Type</th><th>Value</th><th>Reports</th><th>Sources</th><th>Last seen</th><th></th></tr></thead>
                    <tbody>
                        {rows.map((r) => (
                            <tr key={`${r.type}:${r.value}`}>
                                <td>{r.type}</td>
                                <td>{r.value}</td>
                                <td>{r.report_count}</td>
                                <td className="small">{(r.sources || []).join(", ")}</td>
                                <td className="small">{fmt(r.last_seen)}</td>
                                <td><button className="btn btn-sm btn-outline-secondary" onClick={() => onOpenGraph(r.type, r.value)}>Graph</button></td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            )}
        </>
    );
}