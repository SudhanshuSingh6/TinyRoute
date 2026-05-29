export const baseChartOptions = {
  responsive: true,
  maintainAspectRatio: false,
};

// Suppresses fractional tick labels — both charts display integer click counts.
export const integerTickCallback = (value) =>
  Number.isInteger(value) ? value : "";
