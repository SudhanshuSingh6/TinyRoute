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
        data.data.sort(
          (a, b) => new Date(b.createdDate) - new Date(a.createdDate)
        ),
      onError,
      staleTime: 5000,
    }
  );
};

export const useFetchTotalClicks = (
  token,
  onError,
  startDate = `${new Date().getFullYear()}-01-01`,
  endDate   = `${new Date().getFullYear()}-12-31`
) => {
  return useQuery(
    ["url-totalclick", startDate, endDate],
    async () =>
      api.get(`${API.TOTAL_CLICKS}?startDate=${startDate}&endDate=${endDate}`, {
        headers: authHeaders(token),
      }),
    {
      select: (data) =>
        Object.keys(data.data).map((key) => ({
          clickDate: key,
          count: data.data[key],
        })),
      onError,
      staleTime: 5000,
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
    }
  );
};

export const useFetchAnalytics = (
  token,
  shortUrl,
  startDate,
  endDate,
  onError
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
      enabled: !!shortUrl && !!startDate && !!endDate,
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
      enabled: !!id,
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
