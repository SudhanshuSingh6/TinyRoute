// api.js

import axios from "axios";

const api = axios.create({
  baseURL: import.meta.env.VITE_BACKEND_URL,

  withCredentials: true,

  headers: {
    "Content-Type": "application/json",

    Accept: "application/json",
  },
});

// Single-flight refresh: concurrent 401s share one refresh request. Firing a
// refresh per request would replay the already-rotated (now revoked) refresh
// token, which the backend treats as token reuse and responds to by revoking
// the user's entire session — logging them out unexpectedly.
let refreshPromise = null;

const refreshAccessToken = () => {
  if (!refreshPromise) {
    refreshPromise = api.post("/api/auth/public/refresh").finally(() => {
      refreshPromise = null;
    });
  }

  return refreshPromise;
};

api.interceptors.response.use(
  (response) => response,

  async (error) => {
    const originalRequest = error.config;

    const url = originalRequest?.url ?? "";

    const isRefreshRequest = url.includes("/auth/public/refresh");

    const isAuthRequest =
      url.includes("/auth/public/login") ||
      url.includes("/auth/public/register");

    if (
      error.response?.status === 401 &&
      originalRequest &&
      !originalRequest._retry &&
      !isRefreshRequest &&
      !isAuthRequest
    ) {
      originalRequest._retry = true;

      try {
        await refreshAccessToken();

        return await api(originalRequest);
      } catch (refreshError) {
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  },
);

export default api;
