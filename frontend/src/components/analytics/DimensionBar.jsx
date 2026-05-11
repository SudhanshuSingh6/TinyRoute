import PropTypes from "prop-types";

/**
 * A single row showing a label, gradient progress bar, and percentage.
 * Used inside DimensionCard for device / browser / OS / referrer breakdowns.
 */
const DimensionBar = ({ label, count, total }) => {
  const pct = total > 0 ? Math.round((count / total) * 100) : 0;

  return (
    <div className="flex items-center gap-3">
      <span
        className="text-slate-600 text-sm shrink-0 truncate"
        style={{ width: 90 }}
        title={label}
      >
        {label}
      </span>

      <div className="flex-1 h-2 bg-slate-100 rounded-full overflow-hidden">
        <div
          className="h-full rounded-full bg-custom-gradient"
          style={{ width: `${pct}%`, transition: "width 0.6s ease" }}
        />
      </div>

      <span className="text-slate-700 text-xs font-semibold w-8 text-right shrink-0">
        {pct}%
      </span>
    </div>
  );
};

DimensionBar.propTypes = {
  label: PropTypes.string.isRequired,
  count: PropTypes.number.isRequired,
  total: PropTypes.number.isRequired,
};

export default DimensionBar;