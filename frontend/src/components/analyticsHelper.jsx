// src/utils/analyticsHelpers.js

export const getDimensionData = (clicksByDimension, dimensionType) => {
  if (!Array.isArray(clicksByDimension)) return [];
  return clicksByDimension
    .filter((d) => d.dimension === dimensionType)
    .sort((a, b) => b.count - a.count);
};

export const toNumber = (v) => {
  const n = Number(v);
  return Number.isFinite(n) ? n : 0;
};

export const getReturnRate = (totalClicks, uniqueClicks) => {
  if (!totalClicks || totalClicks <= 0) return 0;
  return Math.round(((totalClicks - uniqueClicks) / totalClicks) * 100);
};

export const VELOCITY = {
  UP: {
    label: "↑ Trending Up",
    bg: "#f0fdf4",
    color: "#16a34a",
    border: "#bbf7d0",
  },
  DOWN: {
    label: "↓ Slowing Down",
    bg: "#fef2f2",
    color: "#dc2626",
    border: "#fecaca",
  },
  STABLE: {
    label: "→ Stable",
    bg: "#f8fafc",
    color: "#64748b",
    border: "#e2e8f0",
  },
};

export const getPeakTitle = (type) => {
  switch (type) {
    case "HOUR":
      return "Peak Activity Hour";
    case "DAY":
      return "Peak Activity Day";
    case "WEEK":
      return "Peak Activity Week";
    case "MONTH":
      return "Peak Activity Month";
    default:
      return "Peak Activity";
  }
};

export const formatPeakLabel = (type, label) => {
  if (!label || label === "N/A") return "—";

  switch (type) {
    case "HOUR": {
      const parts = label.split(" ");
      return parts.length === 2 ? parts[1] : label;
    }
    case "DAY":
      return label;
    case "WEEK":
      return label.replace("Week of ", "");
    case "MONTH":
      return label;
    default:
      return label;
  }
};

export const formatBucketLabel = (type, bucket) => {
  if (!bucket) return "";

  switch (type) {
    case "HOUR": {
      const parts = bucket.split(" ");
      return parts.length === 2 ? parts[1] : bucket;
    }
    case "DAY":
      return bucket.slice(5); // MM-DD
    case "WEEK":
      return bucket.replace("Week of ", "");
    case "MONTH":
      return bucket;
    default:
      return bucket;
  }
};

export const getChartData = (analytics) => {
  const buckets = analytics?.clicksByTimeBucket ?? [];

  if (buckets.length > 0) {
    const type = buckets[0]?.type ?? "DAY";

    return {
      type,
      labels: buckets.map((b) => formatBucketLabel(type, b.bucket)),
      values: buckets.map((b) => b.count),
      latestLabel: buckets[buckets.length - 1]?.bucket ?? "",
    };
  }

  const daily = analytics?.dailyClicks ?? [];

  return {
    type: "DAY",
    labels: daily.map((d) => d.date),
    values: daily.map((d) => d.count),
    latestLabel: daily[daily.length - 1]?.date ?? "",
  };
};