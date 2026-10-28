export const API = "http://localhost:8000";

export async function get(path) {
    const r = await fetch(API + path);
    if (!r.ok) throw new Error(`${r.status} ${await r.text()}`);
    return r.json();
}

export async function post(path, body) {
    const r = await fetch(API + path, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
    });
    if (!r.ok) throw new Error(`${r.status} ${await r.text()}`);
    return r.json();
}

export const BAND_CLASS = { CRITICAL: "danger", HIGH: "warning", MEDIUM: "info", LOW: "success" };

// Backend times are UTC without a "Z"; add it so the browser converts correctly
export const fmt = (t) =>
    t ? new Date(/Z$|[+-]\d\d:\d\d$/.test(t) ? t : t + "Z").toLocaleString() : "-";