import { useState } from "react";
import { auth, post } from "../api";
import "./Login.css";
import ThemeToggle from "../components/ThemeToggle";

function ShieldLogo() {
    return (
        <div className="ds-logo">
            <span className="ds-ring" />
            <span className="ds-ring ds-ring-2" />
            <svg viewBox="0 0 64 64" width="72" height="72" aria-hidden="true">
                <defs>
                    <linearGradient id="dsg" x1="0" y1="0" x2="1" y2="1">
                        <stop offset="0" stopColor="var(--ds-cyan)" />
                        <stop offset="1" stopColor="var(--ds-blue)" />
                    </linearGradient>
                </defs>
                <path d="M32 4 8 13v17c0 15.5 10.2 26.8 24 30 13.8-3.2 24-14.5 24-30V13L32 4Z" fill="url(#dsg)" />
                <path d="m21 32 8 8 15-16" fill="none" stroke="var(--ds-bg)" strokeWidth="5"
                    strokeLinecap="round" strokeLinejoin="round" />
            </svg>
        </div>
    );
}

const TITLES = {
    login: ["Welcome, investigator", "Every login uses a fresh code sent to your email."],
    request: ["Request investigator access", "We verify every request. If approved, your first login code is emailed to you."],
};

export default function Login({ onLoggedIn }) {
    const [mode, setMode] = useState("login");
    const [email, setEmail] = useState("");
    const [code, setCode] = useState("");
    const [password, setPassword] = useState("");
    const [showPw, setShowPw] = useState(false);
    const [name, setName] = useState("");
    const [organisation, setOrganisation] = useState("");
    const [reason, setReason] = useState("");
    const [msg, setMsg] = useState(null);
    const [info, setInfo] = useState(null);
    const [ok, setOk] = useState(null);
    const [busy, setBusy] = useState(false);
    const [sending, setSending] = useState(false);

    const switchTo = (m) => { setMode(m); setMsg(null); setInfo(null); setOk(null); };

    const sendCode = async () => {
        if (!email.trim()) { setMsg("Enter your email first."); return; }
        setSending(true); setMsg(null); setInfo(null);
        try {
            const r = await post("/auth/request-login-code", { email });
            setInfo(r.message);
        } catch (e) {
            setMsg(e.message);
        }
            setSending(false);
    };

    const submit = async (e) => {
        e.preventDefault();
        setMsg(null); setOk(null); setBusy(true);
        try {
            if (mode === "request") {
                const r = await post("/auth/request-access", { name, email, organisation, reason });
                setOk(r.message);
            } else {
                const data = await post("/auth/login", { email, code, password });
                if (data.role !== "investigator" && data.role !== "admin") {
                    setMsg("This account is not an investigator account.");
                } else {
                    auth.save(data);
                    onLoggedIn(data);
                }
            }
        } catch (err) {
            setMsg(err.message);
        }
        setBusy(false);
    };

    const [title, sub] = TITLES[mode];

    return (
        <div className="ds-login">
            <ThemeToggle className="ds-theme" />
            <div className="ds-grid" />
            <div className="ds-scan" />

            <div className="ds-wrap">
                <section className="ds-hero">
                    <ShieldLogo />
                    <h1>Digital <span>Shield</span></h1>
                    <p className="ds-tag">Detect. Explain. Protect. Preserve.</p>
                    <ul className="ds-points">
                        <li><b>Real-time</b> scam-call and SMS risk scoring</li>
                        <li><b>SHA-256</b> evidence with a chain of custody</li>
                        <li><b>Threat graph</b> linking numbers, UPI IDs and cases</li>
                    </ul>
                </section>

                <form className="ds-card" onSubmit={submit}>
                    <h2>{title}</h2>
                    <p className="ds-sub">{sub}</p>

                    <div className="ds-switch">
                        <button type="button" className={mode === "login" ? "active" : ""} onClick={() => switchTo("login")}>Log in</button>
                        <button type="button" className={mode === "request" ? "active" : ""} onClick={() => switchTo("request")}>Request access</button>
                    </div>

                    {ok ? (
                        <>
                            <div className="ds-success">{ok}</div>
                            <button type="button" className="ds-btn" onClick={() => switchTo("login")}>Back to log in</button>
                        </>
                    ) : mode === "request" ? (
                        <>
                            <label className="ds-field">
                                <span>Full name</span>
                                <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Inspector name" required />
                            </label>

                            <label className="ds-field">
                                <span>Official email</span>
                                <input type="email" value={email} onChange={(e) => setEmail(e.target.value)}
                                    placeholder="you@department.gov.in" required />
                            </label>

                            <label className="ds-field">
                                <span>Organisation / unit</span>
                                <input value={organisation} onChange={(e) => setOrganisation(e.target.value)}
                                    placeholder="e.g. District Cyber Cell" required />
                            </label>

                            <label className="ds-field">
                                <span>Why do you need access?</span>
                                <input value={reason} onChange={(e) => setReason(e.target.value)}
                                    placeholder="e.g. investigating digital-arrest complaints" />
                            </label>

                            {msg && <div className="ds-error">{msg}</div>}
                            <button className="ds-btn" disabled={busy}>{busy ? "Please wait…" : "Send request"}</button>
                        </>
                    ) : (
                        <>
                            <label className="ds-field">
                                <span>Email</span>
                                <div className="ds-pw">
                                    <input type="email" value={email} onChange={(e) => setEmail(e.target.value)}
                                        placeholder="you@department.gov.in" required style={{ paddingRight: 104 }} />
                                    <button type="button" className="ds-eye" onClick={sendCode} disabled={sending}>
                                        {sending ? "Sending…" : "Send code"}
                                    </button>
                                </div>
                            </label>
                            {info && <div className="ds-success">{info}</div>}

                            <label className="ds-field">
                                <span>Login code</span>
                                <input value={code} onChange={(e) => setCode(e.target.value.toUpperCase())}
                                    placeholder="DS-XXXX-XXXX" required />
                                <div className="ds-hint">First time? Use the code from your approval email.</div>
                            </label>

                            <label className="ds-field">
                                <span>Password <em>(optional)</em></span>
                                <div className="ds-pw">
                                    <input type={showPw ? "text" : "password"} value={password}
                                        onChange={(e) => setPassword(e.target.value)} placeholder="Only if you have set one" />
                                    <button type="button" className="ds-eye" onClick={() => setShowPw(!showPw)}>
                                        {showPw ? "Hide" : "Show"}
                                    </button>
                                </div>
                            </label>

                            {msg && <div className="ds-error">{msg}</div>}
                            <button className="ds-btn" disabled={busy}>{busy ? "Please wait…" : "Log in"}</button>
                        </>
                    )}

                    <p className="ds-foot">🆘 Investigators ONLY 🆘</p>
                </form>
            </div>
        </div>
    );
}