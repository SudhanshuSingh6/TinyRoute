import { useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import Loader from "../components/common/Loader";
import EmptyState from "../components/common/EmptyState";
import StatBlock from "../components/common/StatBlock";
import DateRangePicker, {
  daysAgo,
  today,
} from "../components/common/DateRangePicker";
import Graph from "../components/dashboard/Graph";
import { useStoreContext } from "../contextApi/ContextApi";
import { useFetchAnalytics } from "../hooks/useQuery";

const DAY_ORDER = [
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
  "SUNDAY",
];

const toNumber = (value) => {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
};

const toEntries = (source) => {
  if (!source) return [];
  if (Array.isArray(source)) {
    return source.map((item, index) => [
      item.key ?? item.label ?? item.name ?? item.clickDate ?? String(index),
      item.value ?? item.count ?? item.clicks ?? 0,
    ]);
  }
  return Object.entries(source);
};

const toGraphSeries = (source, { formatLabel, sortNumeric = false } = {}) => {
  const entries = toEntries(source);
  const sorted = [...entries];

  if (sortNumeric) {
    sorted.sort((a, b) => Number(a[0]) - Number(b[0]));
  }

  return sorted.map(([key, value]) => ({
    clickDate: formatLabel ? formatLabel(key) : String(key),
    count: toNumber(value),
  }));
};

const toDayLabel = (day = "") => {
  const value = String(day).toUpperCase();
  if (value.length === 0) return day;
  return value[0] + value.slice(1).toLowerCase();
};

const velocityState = (value) => {
  if (typeof value === "string") {
    const normalized = value.toUpperCase();
    if (normalized.includes("UP")) return "UP";
    if (normalized.includes("DOWN")) return "DOWN";
    return "STABLE";
  }

  const numeric = Number(value);
  if (!Number.isFinite(numeric) || numeric === 0) return "STABLE";
  return numeric > 0 ? "UP" : "DOWN";
};

const VelocityBadge = ({ value }) => {
  const state = velocityState(value);

  const config = {
    UP: {
      text: "UP ↑",
      className: "bg-green-100 text-green-700 border border-green-200",
    },
    DOWN: {
      text: "DOWN ↓",
      className: "bg-red-100 text-red-700 border border-red-200",
    },
    STABLE: {
      text: "STABLE -",
      className: "bg-slate-100 text-slate-600 border border-slate-200",
    },
  };

  return (
    <span
      className={`inline-flex items-center px-3 py-1 rounded-full text-sm font-semibold ${config[state].className}`}
    >
      {config[state].text}
    </span>
  );
};

const BreakdownGrid = ({ title, dataMap }) => {
  const entries = toEntries(dataMap).sort((a, b) => toNumber(b[1]) - toNumber(a[1]));

  return (
    <section className="bg-white border border-slate-200 rounded-xl p-4 shadow-card">
      <h3 className="text-slate-900 font-bold mb-3">{title}</h3>

      {entries.length === 0 ? (
        <p className="text-slate-500 text-sm">No data for this range.</p>
      ) : (
        <div className="grid sm:grid-cols-2 gap-3">
          {entries.map(([label, value]) => (
            <StatBlock key={`${title}-${label}`} label={String(label)} value={toNumber(value)} />
          ))}
        </div>
      )}
    </section>
  );
};

const formatPeakHour = (value) => {
  if (value === null || value === undefined || value === "") return "N/A";
  const numeric = Number(value);
  if (Number.isFinite(numeric)) {
    return `${String(numeric).padStart(2, "0")}:00`;
  }
  return String(value);
};

const AnalyticsPage = () => {
  const { shortUrl } = useParams();
  const navigate = useNavigate();
  const { token } = useStoreContext();

  const [startDate, setStartDate] = useState(daysAgo(30));
  const [endDate, setEndDate] = useState(today());

  const handleDateChange = (start, end) => {
    setStartDate(start);
    setEndDate(end);
  };

  const startDateTime = `${startDate}T00:00:00`;
  const endDateTime = `${endDate}T23:59:59`;

  const onError = () => {
    navigate("/error");
  };

  const { isLoading, data: analytics } = useFetchAnalytics(
    token,
    shortUrl,
    startDateTime,
    endDateTime,
    onError
  );

  const byDateSeries = useMemo(
    () =>
      toGraphSeries(
        analytics?.clicksByDate ??
          analytics?.clicksByDay ??
          analytics?.clicksByDateRange ??
          {}
      ),
    [analytics]
  );

  const byHourSeries = useMemo(
    () =>
      toGraphSeries(analytics?.clicksByHour ?? {}, {
        sortNumeric: true,
        formatLabel: (hour) => `${String(hour).padStart(2, "0")}:00`,
      }),
    [analytics]
  );

  const byDaySeries = useMemo(() => {
    const entries = toEntries(analytics?.clicksByDayOfWeek ?? {});
    const sorted = [...entries].sort((a, b) => {
      const aIndex = DAY_ORDER.indexOf(String(a[0]).toUpperCase());
      const bIndex = DAY_ORDER.indexOf(String(b[0]).toUpperCase());
      return (aIndex === -1 ? 99 : aIndex) - (bIndex === -1 ? 99 : bIndex);
    });

    return sorted.map(([day, value]) => ({
      clickDate: toDayLabel(day),
      count: toNumber(value),
    }));
  }, [analytics]);

  if (isLoading) {
    return <Loader fullPage message="Loading analytics..." />;
  }

  if (!analytics) {
    return (
      <div className="min-h-page flex items-center justify-center px-4">
        <EmptyState title="No analytics available" subtitle="Try another date range." />
      </div>
    );
  }

  return (
    <div className="min-h-page bg-slate-50 lg:px-14 sm:px-8 px-4 py-8">
      <div className="max-w-6xl mx-auto space-y-6">
        <header className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4">
          <div>
            <p className="text-xs uppercase tracking-wide text-slate-500 font-semibold">
              Link Analytics
            </p>
            <h1 className="text-2xl font-bold text-slate-900 break-all">{shortUrl}</h1>
          </div>

          <DateRangePicker
            startDate={startDate}
            endDate={endDate}
            onChange={handleDateChange}
            type="date"
            label="Date Range"
          />
        </header>

        <section className="grid md:grid-cols-4 sm:grid-cols-2 grid-cols-1 gap-4">
          <StatBlock label="Total Clicks" value={toNumber(analytics.totalClicks)} color="blue" />
          <StatBlock
            label="Unique Clicks"
            value={toNumber(analytics.uniqueClicks)}
            color="green"
          />
          <StatBlock label="Peak Hour" value={formatPeakHour(analytics.peakHour)} color="amber" />
          <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-card flex flex-col gap-2">
            <p className="text-slate-500 text-xs font-medium uppercase tracking-wide">
              Click Velocity
            </p>
            <VelocityBadge value={analytics.clickVelocity} />
          </div>
        </section>

        <section className="bg-white border border-slate-200 rounded-xl p-4 shadow-card">
          <h2 className="text-slate-900 font-bold mb-3">Clicks by Date</h2>
          <div className="h-80">
            <Graph graphData={byDateSeries} />
          </div>
        </section>

        <section className="grid lg:grid-cols-2 grid-cols-1 gap-4">
          <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-card">
            <h2 className="text-slate-900 font-bold mb-3">Clicks by Hour</h2>
            <div className="h-80">
              <Graph graphData={byHourSeries} />
            </div>
          </div>

          <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-card">
            <h2 className="text-slate-900 font-bold mb-3">Clicks by Day of Week</h2>
            <div className="h-80">
              <Graph graphData={byDaySeries} />
            </div>
          </div>
        </section>

        <section className="grid lg:grid-cols-2 grid-cols-1 gap-4">
          <BreakdownGrid title="Clicks by Country" dataMap={analytics.clicksByCountry} />
          <BreakdownGrid title="Clicks by Referrer" dataMap={analytics.clicksByReferrer} />
          <BreakdownGrid title="Clicks by Browser" dataMap={analytics.clicksByBrowser} />
          <BreakdownGrid title="Clicks by Device" dataMap={analytics.clicksByDevice} />
          <BreakdownGrid title="Clicks by OS" dataMap={analytics.clicksByOs} />
        </section>
      </div>
    </div>
  );
};

export default AnalyticsPage;
