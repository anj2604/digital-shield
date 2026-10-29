import { useState } from "react";
import { auth } from "./api";
import ThemeToggle from "./components/ThemeToggle";
import Login from "./pages/Login";
import Overview from "./pages/Overview";
import Cases from "./pages/Cases";
import GraphView from "./pages/GraphView";
import ThreatIntel from "./pages/ThreatIntel";
import Osint from "./pages/Osint";
import Account from "./pages/Account";
import AccessRequests from "./pages/AccessRequests";

const BASE_TABS = [
    ["overview", "Overview"],
    ["cases", "Cases"],
    ["graph", "Threat graph"],
    ["intel", "Threat intel"],
    ["osint", "OSINT"],
    ["account", "Account"],
];

export default function App() {
    const [loggedIn, setLoggedIn] = useState(!!auth.token);
    const [tab, setTab] = useState("overview");
    const [graphQuery, setGraphQuery] = useState(null);

    if (!loggedIn) {
        return (
            <Login onLoggedIn={(d) => {
                setLoggedIn(true);
                if (d?.first_login && !d?.has_password) setTab("account");
            }} />
        );
    }

    const TABS = auth.role === "admin" ? [...BASE_TABS, ["access", "Access requests"]] : BASE_TABS;

    const openGraph = (type, value) => {
        setGraphQuery({ type, value });
        setTab("graph");
    };

    return (
        <div className="d-flex" style={{ minHeight: "100vh" }}>
            <nav className="bg-dark text-white p-3 d-flex flex-column" style={{ width: 230 }}>
                <h5 className="mb-1">🛡 Digital Shield</h5>
                <div className="small text-secondary mb-4">Investigator dashboard</div>
                {TABS.map(([key, label]) => (
                    <button key={key}
                        className={`btn w-100 text-start mb-1 ${tab === key ? "btn-primary" : "btn-dark"}`}
                        onClick={() => setTab(key)}>
                        {label}
                    </button>
                ))}
                <div className="mt-auto small text-secondary">
                    <ThemeToggle className="btn-outline-light w-100 mb-3" />
                    Signed in as {auth.name}
                    <button className="btn btn-outline-light btn-sm w-100 mt-2"
                        onClick={() => { auth.clear(); setLoggedIn(false); }}>
                        Log out
                    </button>
                </div>
            </nav>
            <main className="flex-grow-1 p-4 bg-body-tertiary">
                {tab === "overview" && <Overview />}
                {tab === "cases" && <Cases onOpenGraph={openGraph} />}
                {tab === "graph" && <GraphView initial={graphQuery} />}
                {tab === "intel" && <ThreatIntel onOpenGraph={openGraph} />}
                {tab === "osint" && <Osint />}
                {tab === "account" && <Account />}
                {tab === "access" && <AccessRequests />}
            </main>
        </div>
    );
}