import { useQuery } from "react-query";
import api from "../api/api";
import { API } from "../utils/apiRoutes";

export const useFetchMyShortUrls = (onError) => {
  return useQuery("my-shortenurls", async () => api.get(API.MY_URLS), {
    select: (data) =>
      (data.data ?? []).sort(
        (a, b) => new Date(b.createdDate) - new Date(a.createdDate),
      ),
    onError,
    staleTime: 5000,
  });
};

export const useFetchTotalClicks = (onError, startDate, endDate) => {
  const year = new Date().getFullYear();
  const from = startDate || `${year}-01-01`;
  const to = endDate || `${year}-12-31`;

  return useQuery(
    ["url-totalclick", from, to],

    async () => api.get(`${API.TOTAL_CLICKS}?startDate=${from}&endDate=${to}`),

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
      enabled: !!from && !!to,
    },
  );
};

export const useFetchProfile = (onError) => {
  return useQuery("profile", async () => api.get(API.PROFILE), {
    select: (data) => data.data,
    onError,
    staleTime: 10000,
  });
};

export const useFetchAnalytics = (
  shortUrl,
  startDate,
  endDate,
  onError,
  enabled = true,
) => {
  return useQuery(
    ["analytics", shortUrl, startDate, endDate],
    async () =>
      api.get(
        `${API.ANALYTICS(shortUrl)}?startDate=${startDate}&endDate=${endDate}`,
      ),
    {
      select: (data) => data.data,
      onError,
      staleTime: 0,
      enabled: enabled && !!shortUrl && !!startDate && !!endDate,
    },
  );
};

export const useFetchLinkHistory = (shortUrl, onError) => {
  return useQuery(
    ["link-history", shortUrl],
    async () => api.get(API.HISTORY(shortUrl)),

    {
      select: (data) => data.data,
      onError,
      staleTime: 0,

      enabled: !!shortUrl,
    },
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
    },
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
    },
  );
};

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

export const updateProfile = async (data) => {
  const response = await api.put(API.PROFILE, data);

  return response.data;
};
