import { createContext, useContext, useState, useEffect, useCallback } from "react";

const ContextApi = createContext();

export const ContextProvider = ({ children }) => {
  // Initialize token from localStorage only once
  const [token, setTokenState] = useState(() => {
    const storedToken = localStorage.getItem("JWT_TOKEN");
    return storedToken ? JSON.parse(storedToken) : null;
  });

  // Wrapped setToken that also updates localStorage
  const setToken = useCallback((newToken) => {
    setTokenState(newToken);
    if (newToken) {
      localStorage.setItem("JWT_TOKEN", JSON.stringify(newToken));
    } else {
      localStorage.removeItem("JWT_TOKEN");
    }
  }, []);

  // Sync token with localStorage changes (for multi-tab sync)
  useEffect(() => {
    const handleStorageChange = (e) => {
      if (e.key === "JWT_TOKEN") {
        const newToken = e.newValue ? JSON.parse(e.newValue) : null;
        setTokenState(newToken);
      }
    };

    window.addEventListener("storage", handleStorageChange);
    return () => window.removeEventListener("storage", handleStorageChange);
  }, []);

  return (
    <ContextApi.Provider value={{ token, setToken }}>
      {children}
    </ContextApi.Provider>
  );
};

export const useStoreContext = () => {
  const context = useContext(ContextApi);
  if (!context) {
    throw new Error("useStoreContext must be used within ContextProvider");
  }
  return context;
};
