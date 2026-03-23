export const API = {
  // ── Auth ──────────────────────────────────────────────
  LOGIN:    "/api/auth/public/login",
  REGISTER: "/api/auth/public/register",
  PROFILE:  "/api/auth/profile",

  // ── URL management ────────────────────────────────────
  SHORTEN:      "/api/urls/shorten",
  MY_URLS:      "/api/urls/myurls",
  TOTAL_CLICKS: "/api/urls/totalClicks",

  // ── Per-link actions (require id or shortUrl) ─────────
  DELETE:   (id)       => `/api/urls/${id}`,
  EDIT:     (id)       => `/api/urls/${id}`,
  TOGGLE:   (id)       => `/api/urls/${id}/toggle`,
  HISTORY:  (id)       => `/api/urls/${id}/history`,
  ANALYTICS:(shortUrl) => `/api/urls/analytics/${shortUrl}`,
  PREVIEW:  (shortUrl) => `/api/urls/${shortUrl}/preview`,
  QR:       (shortUrl) => `/api/urls/${shortUrl}/qr`,

  // ── Public ────────────────────────────────────────────
  BIO:      (username) => `/api/urls/bio/${username}`,
};
