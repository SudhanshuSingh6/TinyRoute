import PropTypes from "prop-types";
import { baseChartOptions, integerTickCallback } from "../../utils/chartConfig";
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

const ClicksLineChart = ({ labels, values }) => {
  if (!labels?.length || !values?.length) {
    return (
      <div className="h-full flex items-center justify-center">
        <p className="text-slate-400 text-sm">
          No click data found
        </p>
      </div>
    );
  }

  const isSinglePoint = values.length === 1;

  const data = {
    labels,

    datasets: [
      {
        label: "Clicks",

        data: values,

        borderColor: "#3364F7",

        clip: 0,

        backgroundColor: (context) => {
          const chart = context.chart;

          if (!chart.chartArea) {
            return "rgba(51,100,247,0.08)";
          }

          const gradient =
            chart.ctx.createLinearGradient(
              0,
              0,
              0,
              chart.chartArea.bottom
            );

          gradient.addColorStop(
            0,
            "rgba(51,100,247,0.18)"
          );

          gradient.addColorStop(
            1,
            "rgba(147,51,234,0.03)"
          );

          return gradient;
        },

        fill: true,

        tension: isSinglePoint ? 0 : 0.4,

        showLine: !isSinglePoint,

        borderWidth: isSinglePoint ? 0 : 2.5,

        pointRadius: isSinglePoint ? 8 : 4,

        pointHoverRadius: 10,

        pointBackgroundColor: "#3364F7",

        pointBorderColor: "#fff",

        pointBorderWidth: 2,
      },
    ],
  };

  const options = {
    ...baseChartOptions,

    animation: {
      duration: 350,
    },

    interaction: {
      intersect: false,
      mode: "index",
    },

    plugins: {
      legend: {
        display: false,
      },

      tooltip: {
        backgroundColor: "#0f172a",

        titleColor: "#94a3b8",

        bodyColor: "#ffffff",

        borderColor: "#334155",

        borderWidth: 1,

        padding: 12,

        cornerRadius: 10,

        displayColors: false,

        callbacks: {
          label: (ctx) =>
            `${ctx.parsed.y.toLocaleString()} clicks`,
        },
      },
    },

    scales: {
      x: {
        offset: false,

        grid: {
          color: "#f1f5f9",
          drawTicks: false,
        },

        border: {
          display: false,
        },

        ticks: {
          color: "#94a3b8",

          font: {
            size: 11,
            weight: "500",
          },
        },
      },

      y: {
        beginAtZero: true,

        suggestedMax:
          Math.max(...values, 1) + 2,

        grid: {
          color: "#f1f5f9",
        },

        border: {
          display: false,
        },

        ticks: {
          stepSize: 1,

          color: "#94a3b8",

          font: {
            size: 11,
          },

          callback: integerTickCallback,
        },
      },
    },
  };

  return <Line data={data} options={options} />;
};

ClicksLineChart.propTypes = {
  labels: PropTypes.arrayOf(
    PropTypes.string
  ).isRequired,

  values: PropTypes.arrayOf(
    PropTypes.number
  ).isRequired,
};

export default ClicksLineChart;