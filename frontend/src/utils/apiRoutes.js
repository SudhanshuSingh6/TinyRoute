export const API = {
  // ── Auth ──────────────────────────────────────────────
  LOGIN:    "/api/auth/public/login",
  REGISTER: "/api/auth/public/register",
  PROFILE:  "/api/auth/profile",

  // ── URL management ────────────────────────────────────
  SHORTEN:      "/api/urls/shorten",
  MY_URLS:      "/api/urls",
  TOTAL_CLICKS: "/api/urls/total-clicks",

  // ── Per-link actions (require shortUrl) ───────────────
  DELETE:   (shortUrl) => `/api/urls/${shortUrl}`,
  EDIT:     (shortUrl) => `/api/urls/${shortUrl}`,
  DISABLE:  (shortUrl) => `/api/urls/${shortUrl}/disable`,
  ENABLE:   (shortUrl) => `/api/urls/${shortUrl}/enable`,
  HISTORY:  (shortUrl) => `/api/urls/${shortUrl}/history`,
  ANALYTICS:(shortUrl) => `/api/urls/analytics/${shortUrl}`,
  PREVIEW:  (shortUrl) => `/api/urls/${shortUrl}/preview`,
  QR:       (shortUrl) => `/api/urls/${shortUrl}/qr`,

  // ── Public ────────────────────────────────────────────
  BIO:      (username) => `/api/public/users/${username}`,
};
