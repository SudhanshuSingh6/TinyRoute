import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App.jsx";
import "./App.css";
import { ContextProvider } from "./contextApi/ContextApi.jsx";

ReactDOM.createRoot(document.getElementById("root")).render(
  <StrictMode>
    <ContextProvider>
      <App />
    </ContextProvider>
  </StrictMode>
);
