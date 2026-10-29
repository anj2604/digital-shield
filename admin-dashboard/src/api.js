export const API = "http://localhost:8000";

export const auth = {
    get token() { return localStorage.getItem("ds_token"); },
    get name() { return localStorage.getItem("ds_name"); },
    get role() { return localStorage.getItem("ds_role"); },
    save(data) {
        localStorage.setItem("ds_token", data.access_token);
        localStorage.setItem("ds_name", data.name);
        localStorage.setItem("ds_role", data.role);
    },
    clear() {
        ["ds_token", "ds_name", "ds_role"].forEach((k) => localStorage.removeItem(k));
    },
};

async function request(path, options = {}) {
    const headers = { ...(options.headers || {}) };
    const hadToken = !!auth.token;
    if (hadToken) headers.Authorization = `Bearer ${auth.token}`;
    const r = await fetch(API + path, { ...options, headers });
    if (r.status === 401 && hadToken) {
        auth.clear();
        window.location.reload();     // session expired: back to the login page
        throw new Error("Please log in again");
    }
    if (!r.ok) {
        let detail = await r.text();
        try { detail = JSON.parse(detail).detail || detail; } catch { /* plain text */ }
        throw new Error(detail);
    }
    return r.json();
}

export const get = (path) => request(path);
export const post = (path, body) =>
    request(path, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });

export const BAND_CLASS = { CRITICAL: "danger", HIGH: "warning", MEDIUM: "info", LOW: "success" };

export const fmt = (t) =>
    t ? new Date(/Z$|[+-]\d\d:\d\d$/.test(t) ? t : t + "Z").toLocaleString() : "-";