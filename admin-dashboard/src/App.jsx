import { useState } from "react";
import Overview from "./pages/Overview";
import Cases from "./pages/Cases";
import GraphView from "./pages/GraphView";
import ThreatIntel from "./pages/ThreatIntel";

const TABS = [
    ["overview", "Overview"],
    ["cases", "Cases"],
    ["graph", "Threat graph"],
    ["intel", "Threat intel"],
];

export default function App() {
    const [tab, setTab] = useState("overview");
    const [graphQuery, setGraphQuery] = useState(null);

    const openGraph = (type, value) => {
        setGraphQuery({ type, value });
        setTab("graph");
    };

    return (
        <div className="d-flex" style={{ minHeight: "100vh" }}>
            <nav className="bg-dark text-white p-3" style={{ width: 230 }}>
                <h5 className="mb-1">🛡 Digital Shield</h5>
                <div className="small text-secondary mb-4">Investigator dashboard</div>
                {TABS.map(([key, label]) => (
                    <button
                        key={key}
                        className={`btn w-100 text-start mb-1 ${tab === key ? "btn-primary" : "btn-dark"}`}
                        onClick={() => setTab(key)}
                    >
                        {label}
                    </button>
                ))}
            </nav>
            <main className="flex-grow-1 p-4 bg-light">
                {tab === "overview" && <Overview />}
                {tab === "cases" && <Cases onOpenGraph={openGraph} />}
                {tab === "graph" && <GraphView initial={graphQuery} />}
                {tab === "intel" && <ThreatIntel onOpenGraph={openGraph} />}
            </main>
        </div>
    );
}