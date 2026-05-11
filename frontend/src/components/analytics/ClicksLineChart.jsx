import PropTypes from "prop-types";
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

ChartJS.register(
  LineElement,
  PointElement,
  CategoryScale,
  LinearScale,
  Tooltip,
  Filler,
  Legend
);

/**
 * Line chart for click-over-time analytics data.
 * `labels` — x-axis tick strings (e.g. "19:00", "2026-03-29")
 * `values` — y-axis counts matching labels
 */
const ClicksLineChart = ({ labels, values }) => {
  const data = {
    labels,
    datasets: [
      {
        label: "Clicks",
        data: values,
        borderColor: "#3364F7",
        backgroundColor: (ctx) => {
          const gradient = ctx.chart.ctx.createLinearGradient(0, 0, 0, 260);
          gradient.addColorStop(0, "rgba(51, 100, 247, 0.18)");
          gradient.addColorStop(1, "rgba(147, 51, 234, 0.03)");
          return gradient;
        },
        fill: true,
        tension: 0.4,
        borderWidth: 2.5,
        pointBackgroundColor: "#3364F7",
        pointBorderColor: "#fff",
        pointBorderWidth: 2,
        pointRadius: 4,
        pointHoverRadius: 7,
        pointHoverBorderWidth: 2,
      },
    ],
  };

  const options = {
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
        cornerRadius: 8,
        borderColor: "#334155",
        borderWidth: 1,
        callbacks: {
          label: (ctx) => `  ${ctx.parsed.y} clicks`,
        },
      },
    },
    scales: {
      x: {
        grid: { color: "#f1f5f9" },
        ticks: { color: "#94a3b8", font: { size: 11 } },
        border: { display: false },
      },
      y: {
        beginAtZero: true,
        grid: { color: "#f1f5f9" },
        border: { display: false },
        ticks: {
          color: "#94a3b8",
          font: { size: 11 },
          callback: (v) => (Number.isInteger(v) ? v : ""),
          stepSize: 1,
        },
      },
    },
  };

  if (labels.length === 0) {
    return (
      <div className="h-full flex items-center justify-center">
        <p className="text-slate-400 text-sm">No click data in this range.</p>
      </div>
    );
  }

  return <Line data={data} options={options} />;
};

ClicksLineChart.propTypes = {
  labels: PropTypes.arrayOf(PropTypes.string).isRequired,
  values: PropTypes.arrayOf(PropTypes.number).isRequired,
};

export default ClicksLineChart;