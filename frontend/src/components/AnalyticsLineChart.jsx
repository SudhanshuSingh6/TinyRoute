import {
  Chart as ChartJS,
  LineElement,
  PointElement,
  CategoryScale,
  LinearScale,
  Tooltip,
  Filler,
  Legend,
} from "chart.js";
import { Line } from "react-chartjs-2";

ChartJS.register(LineElement, PointElement, CategoryScale, LinearScale, Tooltip, Filler, Legend);

const AnalyticsLineChart = ({ labels, values, title, subtitle, latestLabel }) => {
  const lineChartData = {
    labels,
    datasets: [
      {
        label: "Clicks",
        data: values,
        borderColor: "#3b82f6",
        backgroundColor: (ctx) => {
          const canvas = ctx.chart.ctx;
          const gradient = canvas.createLinearGradient(0, 0, 0, 240);
          gradient.addColorStop(0, "rgba(59,130,246,0.15)");
          gradient.addColorStop(1, "rgba(147,51,234,0.02)");
          return gradient;
        },
        fill: true,
        tension: 0.4,
        borderWidth: 2.5,
        pointBackgroundColor: "#3b82f6",
        pointBorderColor: "#fff",
        pointBorderWidth: 2,
        pointRadius: 5,
        pointHoverRadius: 7,
      },
    ],
  };

  const lineChartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      tooltip: {
        backgroundColor: "#1e293b",
        titleColor: "#94a3b8",
        bodyColor: "#fff",
        bodyFont: { weight: "bold", size: 13 },
        padding: 12,
        cornerRadius: 10,
        borderColor: "#334155",
        borderWidth: 1,
        callbacks: {
          label: (ctx) => ` ${ctx.parsed.y} clicks`,
        },
      },
    },
    scales: {
      x: {
        grid: { color: "#f1f5f9", drawBorder: false },
        ticks: { color: "#94a3b8", font: { size: 11 } },
        border: { display: false },
      },
      y: {
        beginAtZero: true,
        grid: { color: "#f1f5f9", drawBorder: false },
        ticks: {
          color: "#94a3b8",
          font: { size: 11 },
          callback: (v) => (Number.isInteger(v) ? v : ""),
          stepSize: 1,
        },
        border: { display: false },
      },
    },
  };

  return (
    <div className="bg-white border border-slate-200 rounded-xl p-6 shadow-card">
      <div className="flex flex-wrap items-start justify-between gap-3 mb-5">
        <div>
          <h2 className="text-base font-bold text-slate-900">{title}</h2>
          <p className="text-xs text-slate-400 mt-0.5">{subtitle}</p>
        </div>

        {latestLabel && (
          <div className="text-right">
            <p className="text-xs uppercase tracking-wider text-slate-400">Latest data point</p>
            <p className="text-sm font-bold text-btnColor">{latestLabel}</p>
          </div>
        )}
      </div>

      <div style={{ height: 240 }}>
        {labels.length === 0 ? (
          <div className="h-full flex items-center justify-center">
            <p className="text-slate-400 text-sm">No click data in this range</p>
          </div>
        ) : (
          <Line data={lineChartData} options={lineChartOptions} />
        )}
      </div>
    </div>
  );
};

export default AnalyticsLineChart;