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

api.interceptors.response.use(
  (response) => response,

  async (error) => {
    const originalRequest = error.config;

    const isRefreshRequest = originalRequest.url.includes(
      "/auth/public/refresh",
    );

    const isAuthRequest =
      originalRequest.url.includes("/auth/public/login") ||
      originalRequest.url.includes("/auth/public/register");

    if (
      error.response?.status === 401 &&
      !originalRequest._retry &&
      !isRefreshRequest &&
      !isAuthRequest
    ) {
      originalRequest._retry = true;

      try {
        await api.post("/api/auth/public/refresh");

        return await api(originalRequest);
      } catch (refreshError) {
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  },
);

export default api;
