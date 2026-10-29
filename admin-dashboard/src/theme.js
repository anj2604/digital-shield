const KEY = "ds_theme";

export function getTheme() {
    const saved = localStorage.getItem(KEY);
    if (saved === "light" || saved === "dark") return saved;
    return window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

export function setTheme(theme) {
    localStorage.setItem(KEY, theme);
    document.documentElement.setAttribute("data-bs-theme", theme);
}

export function initTheme() {
    document.documentElement.setAttribute("data-bs-theme", getTheme());
}