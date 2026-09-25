import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { LatchApp } from "./components/latch-app";
import "./styles.css";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <LatchApp />
  </StrictMode>,
);
