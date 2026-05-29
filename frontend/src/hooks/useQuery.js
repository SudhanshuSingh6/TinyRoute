// queries.js

import { useQuery } from "react-query";

import api from "../api/api";

import { API } from "../utils/apiRoutes";

// ─────────────────────────────────────────────
// My URLs
// ─────────────────────────────────────────────

export const useFetchMyShortUrls = (onError) => {
  return useQuery(
    "my-shortenurls",

    async () => {
      const response = await api.get(API.MY_URLS);

      return response.data;
    },

    {
      select: (data) => (data ?? []).slice().sort((a, b) => b.id - a.id),

      onError,

      retry: 1,

      staleTime: 5000,

      refetchOnWindowFocus: false,
    },
  );
};

// ─────────────────────────────────────────────
// Total Clicks
// ─────────────────────────────────────────────

export const useFetchTotalClicks = ({ onError, startDate, endDate }) => {
  const year = new Date().getFullYear();

  const from = startDate || `${year}-01-01`;

  const to = endDate || `${year}-12-31`;

  return useQuery(
    ["url-totalclick", from, to],

    async () => {
      const response = await api.get(
        `${API.TOTAL_CLICKS}?startDate=${from}&endDate=${to}`,
      );

      return response.data;
    },

    {
      select: (data) =>
        Object.entries(data ?? {})
          .sort(([a], [b]) => new Date(a) - new Date(b))
          .map(([clickDate, count]) => ({
            clickDate,
            count: Number(count) || 0,
          })),

      onError,

      retry: 1,

      staleTime: 30000,

      refetchOnWindowFocus: false,

      enabled: !!from && !!to,
    },
  );
};

// ─────────────────────────────────────────────
// Profile
// ─────────────────────────────────────────────

export const useFetchProfile = (options = {}) => {
  return useQuery(
    "profile",

    async () => {
      const response = await api.get(API.PROFILE);

      return response.data;
    },

    {
      retry: 1,

      staleTime: 10000,

      refetchOnWindowFocus: false,

      ...options,
    },
  );
};

// ─────────────────────────────────────────────
// Historical Analytics (PostgreSQL)
// ─────────────────────────────────────────────

export const useFetchAnalytics = ({
  shortUrl,
  startDate,
  endDate,
  onError,
  enabled = true,
}) => {
  return useQuery(
    ["analytics", shortUrl, startDate, endDate],

    async () => {
      const response = await api.get(
        `${API.ANALYTICS(shortUrl)}?startDate=${startDate}&endDate=${endDate}`,
      );

      return response.data;
    },

    {
      onError,

      retry: 1,

      staleTime: 30000,

      refetchOnWindowFocus: false,

      enabled: enabled && !!shortUrl && !!startDate && !!endDate,
    },
  );
};

// ─────────────────────────────────────────────
// Live Analytics (Redis)
// ─────────────────────────────────────────────

export const useFetchLiveAnalytics = ({
  shortUrl,
  onError,
  enabled = true,
}) => {
  return useQuery(
    ["live-analytics", shortUrl],

    async () => {
      const response = await api.get(API.LIVE_ANALYTICS(shortUrl));

      return response.data;
    },

    {
      onError,

      retry: 1,

      staleTime: 0,

      refetchInterval: 3000,

      refetchOnWindowFocus: false,

      enabled: enabled && !!shortUrl,
    },
  );
};

// ─────────────────────────────────────────────
// Link History
// ─────────────────────────────────────────────

export const useFetchLinkHistory = (shortUrl, onError) => {
  return useQuery(
    ["link-history", shortUrl],

    async () => {
      const response = await api.get(API.HISTORY(shortUrl));

      return response.data;
    },

    {
      onError,

      retry: 1,

      staleTime: 30000,

      refetchOnWindowFocus: false,

      enabled: !!shortUrl,
    },
  );
};

// ─────────────────────────────────────────────
// Link Preview
// ─────────────────────────────────────────────

export const useFetchLinkPreview = ({ shortUrl, onError, enabled = true }) => {
  return useQuery(
    ["link-preview", shortUrl],

    async () => {
      const response = await api.get(API.PREVIEW(shortUrl));

      return response.data;
    },

    {
      onError,

      retry: 1,

      staleTime: 60000,

      refetchOnWindowFocus: false,

      enabled: enabled && !!shortUrl,
    },
  );
};

// ─────────────────────────────────────────────
// Bio Page
// ─────────────────────────────────────────────

export const useFetchBioPage = (username, onError) => {
  return useQuery(
    ["bio-page", username],

    async () => {
      const response = await api.get(API.BIO(username));

      return response.data;
    },

    {
      onError,

      retry: 1,

      staleTime: 30000,

      refetchOnWindowFocus: false,

      enabled: !!username,
    },
  );
};

// ─────────────────────────────────────────────
// Mutations
// ─────────────────────────────────────────────

export const createShortUrl = async (data) => {
  const response = await api.post(API.SHORTEN, data);

  return response.data;
};

export const deleteShortUrl = async (shortUrl) => {
  await api.delete(API.DELETE(shortUrl));
};

export const disableShortUrl = async (shortUrl) => {
  const response = await api.patch(API.DISABLE(shortUrl), null);

  return response.data;
};

export const enableShortUrl = async (shortUrl) => {
  const response = await api.patch(API.ENABLE(shortUrl), null);

  return response.data;
};

export const editShortUrl = async (shortUrl, data) => {
  const response = await api.put(API.EDIT(shortUrl), data);

  return response.data;
};

export const editShortUrlExpiry = async (shortUrl, data) => {
  const response = await api.patch(API.EDIT_EXPIRY(shortUrl), data);

  return response.data;
};

export const updateProfile = async (data) => {
  const response = await api.put(API.PROFILE, data);

  return response.data;
};
