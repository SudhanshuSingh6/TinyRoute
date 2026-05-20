import { useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

// ── existing common components ────────────────────────────────────────────────
import Loader from "../components/Common/Loader";
import EmptyState from "../components/Common/EmptyState";
import StatBlock from "../components/Common/StatBlock";
import DateRangePicker, {
  daysAgo,
  today,
} from "../components/Common/DateRangePicker";

// ── analytics components ──────────────────────────────────────────────────────
import VelocityBadge from "../components/Analytics/VelocityBadge";
import DimensionCard from "../components/Analytics/DimensionCard";
import DimensionBar from "../components/Analytics/DimensionBar";
import ClicksLineChart from "../components/Analytics/ClicksLineChart";
import PeakActivityCard from "../components/Analytics/PeakActivityCard";

// ── data / context ────────────────────────────────────────────────────────────
import { useStoreContext } from "../contextApi/ContextApi";
import { useFetchAnalytics } from "../hooks/useQuery";

// ──────────────────────────────────────────────────────────────────────────────
// Helpers
// ──────────────────────────────────────────────────────────────────────────────

const getDimension = (list, type) => {
  if (!Array.isArray(list)) return [];

  return list
    .filter((d) => d.dimension === type)
    .sort((a, b) => b.count - a.count);
};

const toNum = (v) => {
  const n = Number(v);
  return Number.isFinite(n) ? n : 0;
};

/**
 * Build chart-ready data.
 */
const buildChartData = (timeBuckets = []) => {
  if (!timeBuckets.length) {
    return {
      labels: [],
      values: [],
      latestLabel: "",
    };
  }

  const type = timeBuckets[0]?.type;

  const labels = timeBuckets.map((b) => {
    switch (type) {
      case "HOUR":
        // "2026-04-02 19:00" → "19:00"
        return b.bucket.split(" ")[1];

      case "DAY": {
        // "2026-04-02" → "2 Apr"
        const [year, month, day] = b.bucket.split("-");

        return `${Number(day)} ${new Date(year, month - 1).toLocaleString(
          "en-IN",
          {
            month: "short",
          },
        )}`;
      }

      case "WEEK": {
        // "Week of 2026-03-30" → "5 Apr"

        const dateString = b.bucket.replace("Week of ", "");

        const [year, month, day] = dateString.split("-");

        const date = new Date(year, month - 1, day);

        date.setDate(date.getDate() + 6);

        return `${date.getDate()} ${date.toLocaleString("en-IN", {
          month: "short",
        })}`;
      }

      case "MONTH": {
        // "2026-05" → "May 2026"

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
};

const formatPeakLabel = (raw) => {
  if (!raw || raw === "N/A") return "—";

  const parts = raw.split(" ");

  return parts.length === 2 ? parts[1] : raw;
};

// ──────────────────────────────────────────────────────────────────────────────
// Velocity Card
// ──────────────────────────────────────────────────────────────────────────────

const VelocityStatCard = ({ trend }) => (
  <div className="bg-white border border-slate-200 rounded-xl p-4 flex flex-col gap-2 shadow-card hover:shadow-md transition-shadow duration-200">
    <p className="text-slate-500 text-xs font-medium uppercase tracking-wide">
      Click Velocity
    </p>

    <VelocityBadge trend={trend} showSub />
  </div>
);

// ──────────────────────────────────────────────────────────────────────────────
// Page
// ──────────────────────────────────────────────────────────────────────────────

const AnalyticsPage = () => {
  const { shortUrl } = useParams();

  const navigate = useNavigate();

  const { token } = useStoreContext();

  const [startDate, setStartDate] = useState(daysAgo(30));

  const [endDate, setEndDate] = useState(today());

  const startDateTime = `${startDate}T00:00:00`;

  const endDateTime = `${endDate}T23:59:59`;

  const { isLoading, data: analytics } = useFetchAnalytics(
    token,
    shortUrl,
    startDateTime,
    endDateTime,
    () => navigate("/error"),
  );

  // ────────────────────────────────────────────────────────────────────────────
  // Derived Data
  // ────────────────────────────────────────────────────────────────────────────

  const countries = useMemo(
    () => getDimension(analytics?.clicksByDimension, "COUNTRY"),
    [analytics],
  );

  const devices = useMemo(
    () => getDimension(analytics?.clicksByDimension, "DEVICE"),
    [analytics],
  );

  const browsers = useMemo(
    () => getDimension(analytics?.clicksByDimension, "BROWSER"),
    [analytics],
  );

  const osData = useMemo(
    () => getDimension(analytics?.clicksByDimension, "OS"),
    [analytics],
  );

  const referrers = useMemo(
    () => getDimension(analytics?.clicksByDimension, "REFERRER"),
    [analytics],
  );

  const chart = useMemo(
    () => buildChartData(analytics?.clicksByTimeBucket),
    [analytics],
  );

  const totalClicks = toNum(analytics?.totalClicks);

  const uniqueClicks = toNum(analytics?.uniqueClicks);

  const returnRate =
    totalClicks > 0
      ? Math.round(((totalClicks - uniqueClicks) / totalClicks) * 100)
      : 0;

  const peakLabel = formatPeakLabel(analytics?.peakActivity?.label);

  const peakType = formatPeakLabel(analytics?.peakActivity?.type);

  const peakCount = toNum(analytics?.peakActivity?.count);

  const velocityTrend = analytics?.clickVelocity?.trend ?? "STABLE";

  const geoTotal = useMemo(
    () => countries.reduce((s, c) => s + c.count, 0),
    [countries],
  );

  // ────────────────────────────────────────────────────────────────────────────
  // Loading / Empty
  // ────────────────────────────────────────────────────────────────────────────

  if (isLoading) {
    return <Loader fullPage message="Loading analytics..." />;
  }

  if (!analytics) {
    return (
      <div className="min-h-page flex items-center justify-center px-4">
        <EmptyState
          title="No analytics available"
          subtitle="Try adjusting the date range."
        />
      </div>
    );
  }

  // ────────────────────────────────────────────────────────────────────────────
  // Render
  // ────────────────────────────────────────────────────────────────────────────

  return (
    <div className="min-h-page bg-slate-50 lg:px-14 sm:px-8 px-4 py-8">
      <div className="max-w-6xl mx-auto space-y-6">
        {/* Header */}

        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <p className="text-xs font-semibold uppercase tracking-widest text-slate-400 mb-1">
              Link Analytics
            </p>

            <h1 className="text-2xl font-bold font-montserrat text-slate-900 break-all">
              <span className="text-slate-400">url/</span>

              {shortUrl}
            </h1>
          </div>

          <div className="bg-white border border-slate-200 rounded-xl px-4 py-3 shadow-card self-start">
            <DateRangePicker
              startDate={startDate}
              endDate={endDate}
              onChange={(s, e) => {
                setStartDate(s);
                setEndDate(e);
              }}
              type="date"
              label="Range"
            />
          </div>
        </div>

        {/* Stats */}

        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <StatBlock
            label="Total Clicks"
            value={totalClicks.toLocaleString()}
            sub="All tracked interactions"
            color="blue"
          />

          <StatBlock
            label="Unique Clicks"
            value={uniqueClicks.toLocaleString()}
            sub={`${returnRate}% return rate`}
            color="green"
          />

          <StatBlock
            label={peakType}
            value={peakLabel}
            sub={`${peakCount} clicks at peak`}
            color="amber"
          />

          <VelocityStatCard trend={velocityTrend} />
        </div>

        {/* Chart */}

        <div className="grid gap-4 lg:grid-cols-[1fr_256px]">
          <div className="bg-white border border-slate-200 rounded-xl p-6 shadow-card">
            <div className="flex flex-wrap items-start justify-between gap-3 mb-5">
              <div>
                <h2 className="text-base font-bold font-montserrat text-slate-900">
                  Clicks by Time Bucket
                </h2>

                <p className="text-xs text-slate-400 mt-0.5">
                  {startDate} → {endDate}
                </p>
              </div>

              {chart.latestLabel && (
                <div className="text-right">
                  <p className="text-xs uppercase tracking-wider text-slate-400">
                    Latest data point
                  </p>

                  <p className="text-sm font-bold text-btnColor">
                    {chart.latestLabel}
                  </p>
                </div>
              )}
            </div>

            <div className="h-60">
              <ClicksLineChart labels={chart.labels} values={chart.values} />
            </div>
          </div>

          <PeakActivityCard
            type={peakType}
            label={peakLabel}
            count={peakCount}
          />
        </div>

        {/* Dimensions */}

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <DimensionCard title="Device" icon="📱" data={devices} />

          <DimensionCard title="Browser" icon="🌐" data={browsers} />

          <DimensionCard title="Operating System" icon="💻" data={osData} />

          <DimensionCard title="Referral Sources" icon="🔗" data={referrers} />
        </div>

        {/* Geography */}

        {countries.length > 0 && (
          <div className="bg-white border border-slate-200 rounded-xl p-6 shadow-card">
            <div className="flex items-center gap-3 mb-5">
              <div className="w-8 h-8 rounded-lg bg-custom-gradient flex items-center justify-center text-white text-sm shrink-0">
                🌍
              </div>

              <div>
                <h2 className="text-base font-bold font-montserrat text-slate-900">
                  Geographic Origin
                </h2>

                <p className="text-xs text-slate-400">
                  Active sessions by region
                </p>
              </div>
            </div>

            <div className="max-w-2xl flex flex-col gap-3">
              {countries.slice(0, 8).map((c) => (
                <DimensionBar
                  key={c.key}
                  label={c.key}
                  count={c.count}
                  total={geoTotal}
                />
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default AnalyticsPage;
