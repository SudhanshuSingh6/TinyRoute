import { useQuery } from "react-query";
import api from "../api/api";
import { API } from "../utils/apiRoutes";

const authHeaders = (token) => ({
  "Content-Type": "application/json",
  Accept: "application/json",
  Authorization: "Bearer " + token,
});

export const useFetchMyShortUrls = (token, onError) => {
  return useQuery(
    "my-shortenurls",
    async () => api.get(API.MY_URLS, { headers: authHeaders(token) }),
    {
      select: (data) =>
        (data.data ?? []).sort(
          (a, b) => new Date(b.createdDate) - new Date(a.createdDate)
        ),
      onError,
      staleTime: 5000,
      enabled: !!token,
    }
  );
};

export const useFetchTotalClicks = (token, onError, startDate, endDate) => {
  const year = new Date().getFullYear();
  const from = startDate || `${year}-01-01`;
  const to = endDate || `${year}-12-31`;

  return useQuery(
    ["url-totalclick", from, to],
    async () =>
      api.get(`${API.TOTAL_CLICKS}?startDate=${from}&endDate=${to}`, {
        headers: authHeaders(token),
      }),
    {
      select: (data) =>
        Object.entries(data.data ?? {})
          .sort(([a], [b]) => new Date(a) - new Date(b))
          .map(([clickDate, count]) => ({
            clickDate,
            count: Number(count) || 0,
          })),
      onError,
      staleTime: 5000,
      enabled: !!token && !!from && !!to,
    }
  );
};

export const useFetchProfile = (token, onError) => {
  return useQuery(
    "profile",
    async () => api.get(API.PROFILE, { headers: authHeaders(token) }),
    {
      select: (data) => data.data,
      onError,
      staleTime: 10000,
      enabled: !!token,
    }
  );
};

export const useFetchAnalytics = (
  token,
  shortUrl,
  startDate,
  endDate,
  onError,
  enabled = true
) => {
  return useQuery(
    ["analytics", shortUrl, startDate, endDate],
    async () =>
      api.get(
        `${API.ANALYTICS(shortUrl)}?startDate=${startDate}&endDate=${endDate}`,
        { headers: authHeaders(token) }
      ),
    {
      select: (data) => data.data,
      onError,
      staleTime: 0,
      enabled: enabled && !!token && !!shortUrl && !!startDate && !!endDate,
    }
  );
};

export const useFetchLinkHistory = (token, id, onError) => {
  return useQuery(
    ["link-history", id],
    async () => api.get(API.HISTORY(id), { headers: authHeaders(token) }),
    {
      select: (data) => data.data,
      onError,
      staleTime: 0,
      enabled: !!token && !!id,
    }
  );
};

export const useFetchLinkPreview = (shortUrl, onError) => {
  return useQuery(
    ["link-preview", shortUrl],
    async () => api.get(API.PREVIEW(shortUrl)),
    {
      select: (data) => data.data,
      onError,
      staleTime: 60000,
      enabled: !!shortUrl,
    }
  );
};

export const useFetchBioPage = (username, onError) => {
  return useQuery(
    ["bio-page", username],
    async () => api.get(API.BIO(username)),
    {
      select: (data) => data.data,
      onError,
      staleTime: 30000,
      enabled: !!username,
    }
  );
};

export const createShortUrl = async (token, data) => {
  const response = await api.post(API.SHORTEN, data, {
    headers: authHeaders(token),
  });
  return response.data;
};

export const deleteShortUrl = async (token, id) => {
  await api.delete(API.DELETE(id), { headers: authHeaders(token) });
};

export const toggleShortUrl = async (token, id) => {
  const response = await api.patch(API.TOGGLE(id), null, {
    headers: authHeaders(token),
  });
  return response.data;
};

export const editShortUrl = async (token, id, data) => {
  const response = await api.put(API.EDIT(id), data, {
    headers: authHeaders(token),
  });
  return response.data;
};

export const updateProfile = async (token, data) => {
  const response = await api.put(API.PROFILE, data, {
    headers: authHeaders(token),
  });
  return response.data;
};
