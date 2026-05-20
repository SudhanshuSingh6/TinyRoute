import PropTypes from "prop-types";

const CONFIGS = {
  UP: {
    label: "↑ Trending Up",
    className: "bg-green-100 text-green-700 border border-green-200",
    sub: "Growing vs previous period",
  },
  DOWN: {
    label: "↓ Slowing Down",
    className: "bg-red-100 text-red-600 border border-red-200",
    sub: "Declining vs previous period",
  },
  STABLE: {
    label: "→ Stable",
    className: "bg-slate-100 text-slate-500 border border-slate-200",
    sub: "Consistent with previous period",
  },
};

const VelocityBadge = ({ trend, showSub }) => {
  const cfg = CONFIGS[trend] ?? CONFIGS.STABLE;

  return (
    <div className="flex flex-col gap-1">
      <span
        className={`inline-flex items-center self-start px-2.5 py-1 rounded-lg text-sm font-semibold ${cfg.className}`}
      >
        {cfg.label}
      </span>
      {showSub && (
        <span className="text-xs text-slate-400">{cfg.sub}</span>
      )}
    </div>
  );
};

VelocityBadge.propTypes = {
  trend: PropTypes.oneOf(["UP", "DOWN", "STABLE"]),
  showSub: PropTypes.bool,
};

VelocityBadge.defaultProps = {
  trend: "STABLE",
  showSub: false,
};

export default VelocityBadge;