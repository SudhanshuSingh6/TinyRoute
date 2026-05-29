export function getDimension(list, type) {
  if (!Array.isArray(list)) return [];
  return list
    .filter((d) => d.dimension === type)
    .sort((a, b) => b.count - a.count);
}

export function toNum(v) {
  const n = Number(v);
  return Number.isFinite(n) ? n : 0;
}

export function formatPeakLabel(raw) {
  if (!raw || raw === "N/A") return "—";
  const parts = raw.split(" ");
  return parts.length === 2 ? parts[1] : raw;
}

function buildChartData(timeBuckets = []) {
  if (!timeBuckets.length) {
    return { labels: [], values: [], latestLabel: "" };
  }

  const type = timeBuckets[0]?.type;

  const labels = timeBuckets.map((b) => {
    switch (type) {
      case "HOUR":
        return b.bucket.split(" ")[1];

      case "DAY": {
        const [year, month, day] = b.bucket.split("-");
        return `${Number(day)} ${new Date(year, month - 1).toLocaleString("en-IN", { month: "short" })}`;
      }

      case "WEEK": {
        const dateString = b.bucket.replace("Week of ", "");
        const [year, month, day] = dateString.split("-");
        const date = new Date(year, month - 1, day);
        date.setDate(date.getDate() + 6);
        return `${date.getDate()} ${date.toLocaleString("en-IN", { month: "short" })}`;
      }

      case "MONTH": {
        const [year, month] = b.bucket.split("-");
        return new Date(year, month - 1).toLocaleString("en-IN", {
          month: "short",
          year: "numeric",
        });
      }

      default:
        return b.bucket;
    }
  });

  return {
    labels,
    values: timeBuckets.map((b) => b.count),
    latestLabel: labels[labels.length - 1] ?? "",
  };
}

// Builds chart data from analytics, patching the current time bucket with
// live click counts when they exceed the historical value.
export function buildChart(analytics, liveAnalytics) {
  const timeBuckets = analytics?.clicksByTimeBucket;
  const built = buildChartData(timeBuckets);

  if (!timeBuckets?.length || !liveAnalytics?.todayClicks) {
    return built;
  }

  const patchedValues = [...built.values];
  const now = new Date();
  const localDate = now.toLocaleDateString("en-CA");
  const currentHour = `${localDate} ${String(now.getHours()).padStart(2, "0")}`;

  const currentIndex = timeBuckets.findIndex((bucket) => {
    switch (bucket.type) {
      case "DAY":
        return bucket.bucket === localDate;

      case "MONTH": {
        const monthKey = localDate.slice(0, 7);
        return bucket.bucket === monthKey;
      }

      case "WEEK": {
        const start = new Date(bucket.bucket.replace("Week of ", ""));
        const end = new Date(start);
        end.setDate(end.getDate() + 6);
        return now >= start && now <= end;
      }

      case "HOUR":
        return bucket.bucket.startsWith(currentHour);

      default:
        return false;
    }
  });

  if (currentIndex !== -1) {
    const liveValue = liveAnalytics.todayClicks;
    if (liveValue > built.values[currentIndex]) {
      patchedValues[currentIndex] = liveValue;
    }
  }

  return { ...built, values: patchedValues };
}
