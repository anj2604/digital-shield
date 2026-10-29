import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "bootstrap/dist/css/bootstrap.min.css";
import { initTheme } from "./theme";
import App from "./App.jsx";

initTheme();  

createRoot(document.getElementById("root")).render(
    <StrictMode>
        <App />
    </StrictMode>
);