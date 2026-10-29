import { useState } from "react";
import { getTheme, setTheme } from "../theme";

export default function ThemeToggle({ className = "" }) {
    const [theme, setT] = useState(getTheme());

    const flip = () => {
        const next = theme === "dark" ? "light" : "dark";
        setTheme(next);
        setT(next);
    };

    return (
        <button type="button" className={`btn btn-sm ${className}`} onClick={flip}
        aria-label={`Switch to ${theme === "dark" ? "light" : "dark"} mode`}>
        {theme === "dark" ? "☀️ Light mode" : "🌙 Dark mode"}
        </button>
    );
}