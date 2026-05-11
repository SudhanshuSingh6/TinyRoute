import { useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

// ── existing common components (unchanged) ────────────────────────────────────
import Loader from "../components/common/Loader";
import EmptyState from "../components/common/EmptyState";
import StatBlock from "../components/common/StatBlock";
import DateRangePicker, {
  daysAgo,
  today,
} from "../components/common/DateRangePicker";

// ── new analytics-specific reusable components ────────────────────────────────
import VelocityBadge from "../components/analytics/VelocityBadge";
import DimensionCard from "../components/analytics/DimensionCard";
import DimensionBar from "../components/analytics/DimensionBar";
import ClicksLineChart from "../components/analytics/ClicksLineChart";
import PeakActivityCard from "../components/analytics/PeakActivityCard";

// ── data / context ─────────────────────────────────────────────────────────────
import { useStoreContext } from "../contextApi/ContextApi";
import { useFetchAnalytics } from "../hooks/useQuery";

// ─── helpers ──────────────────────────────────────────────────────────────────

/** Extract and sort items for one dimension type */
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
 * Build chart-ready { labels, values, latestLabel } from the
 * clicksByTimeBucket array (falling back to dailyClicks).
 * HOUR buckets like "2026-03-29 19:00" are trimmed to "19:00".
 */
const buildChartData = (timeBuckets = [], dailyClicks = []) => {
  if (timeBuckets.length > 0) {
    return {
      labels: timeBuckets.map((b) => {
        const parts = b.bucket.split(" ");
        return parts.length === 2 ? parts[1] : b.bucket;
      }),
      values: timeBuckets.map((b) => toNum(b.count)),
      latestLabel: timeBuckets[timeBuckets.length - 1]?.bucket ?? "",
    };
  }
  return {
    labels: dailyClicks.map((d) => d.date),
    values: dailyClicks.map((d) => toNum(d.count)),
    latestLabel: dailyClicks[dailyClicks.length - 1]?.date ?? "",
  };
};

/** "2026-03-29 19:00" → "19:00", anything else is returned as-is */
const formatPeakLabel = (raw) => {
  if (!raw || raw === "N/A") return "—";
  const parts = raw.split(" ");
  return parts.length === 2 ? parts[1] : raw;
};

// ─── Velocity stat card ───────────────────────────────────────────────────────
// StatBlock from the codebase only renders a string value, so for the velocity
// we build a matching card manually that fits the same visual grid slot.

const VelocityStatCard = ({ trend }) => (
  <div className="bg-white border border-slate-200 rounded-xl p-4 flex flex-col gap-2 shadow-card hover:shadow-md transition-shadow duration-200">
    <p className="text-slate-500 text-xs font-medium uppercase tracking-wide">
      Click Velocity
    </p>
    <VelocityBadge trend={trend} showSub />
  </div>
);

// ─── page ──────────────────────────────────────────────────────────────────────

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
    () => navigate("/error")
  );

  // ── derived data ─────────────────────────────────────────────────────────────
  const countries = useMemo(
    () => getDimension(analytics?.clicksByDimension, "COUNTRY"),
    [analytics]
  );
  const devices = useMemo(
    () => getDimension(analytics?.clicksByDimension, "DEVICE"),
    [analytics]
  );
  const browsers = useMemo(
    () => getDimension(analytics?.clicksByDimension, "BROWSER"),
    [analytics]
  );
  const osData = useMemo(
    () => getDimension(analytics?.clicksByDimension, "OS"),
    [analytics]
  );
  const referrers = useMemo(
    () => getDimension(analytics?.clicksByDimension, "REFERRER"),
    [analytics]
  );

  const chart = useMemo(
    () => buildChartData(analytics?.clicksByTimeBucket, analytics?.dailyClicks),
    [analytics]
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
    [countries]
  );

  // ── loading / empty guards ────────────────────────────────────────────────────
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

  // ── render ────────────────────────────────────────────────────────────────────
  return (
    <div className="min-h-page bg-slate-50 lg:px-14 sm:px-8 px-4 py-8">
      <div className="max-w-6xl mx-auto space-y-6">

        {/* ── Page header ── */}
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

          {/* Existing DateRangePicker, wrapped in a card shell */}
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

        {/* ── 4-column stat row ── */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          {/* Existing StatBlock component × 3 */}
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

          {/* VelocityStatCard — matches StatBlock visual dimensions */}
          <VelocityStatCard trend={velocityTrend} />
        </div>

        {/* ── Chart + Peak sidebar ── */}
        <div className="grid gap-4 lg:grid-cols-[1fr_256px]">

          {/* Line chart card */}
          <div className="bg-white border border-slate-200 rounded-xl p-6 shadow-card">
            <div className="flex flex-wrap items-start justify-between gap-3 mb-5">
              <div>
                <h2 className="text-base font-bold font-montserrat text-slate-900">
                  Clicks by Time Bucket
                </h2>
                <p className="text-xs text-slate-400 mt-0.5">
                  {startDate} → {endDate} · Real-time tracking
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

            {/* ClicksLineChart — new reusable component */}
            <div className="h-60">
              <ClicksLineChart labels={chart.labels} values={chart.values} />
            </div>
          </div>

          {/* PeakActivityCard — new reusable component */}
          <PeakActivityCard type ={peakType} label={peakLabel} count={peakCount} />
        </div>

        {/* ── Dimension breakdown grid ── */}
        {/* DimensionCard — new reusable component (×4) */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <DimensionCard title="Device"           icon="📱" data={devices}   />
          <DimensionCard title="Browser"          icon="🌐" data={browsers}  />
          <DimensionCard title="Operating System" icon="💻" data={osData}    />
          <DimensionCard title="Referral Sources" icon="🔗" data={referrers} />
        </div>

        {/* ── Geographic origin ── */}
        {countries.length > 0 && (
          <div className="bg-white border border-slate-200 rounded-xl p-6 shadow-card">
            {/* Section header */}
            <div className="flex items-center gap-3 mb-5">
              <div className="w-8 h-8 rounded-lg bg-custom-gradient flex items-center justify-center text-white text-sm shrink-0">
                🌍
              </div>
              <div>
                <h2 className="text-base font-bold font-montserrat text-slate-900">
                  Geographic Origin
                </h2>
                <p className="text-xs text-slate-400">Active sessions by region</p>
              </div>
            </div>

            {/* DimensionBar — new reusable component used directly (wider layout) */}
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