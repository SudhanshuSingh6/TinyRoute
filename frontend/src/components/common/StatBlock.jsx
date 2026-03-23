import PropTypes from "prop-types";

const COLOR_MAP = {
  default: "text-slate-800",
  blue:    "text-blue-600",
  green:   "text-green-600",
  red:     "text-red-600",
  amber:   "text-amber-600",
  purple:  "text-purple-600",
};

const StatBlock = ({ label, value, sub, color, className }) => {
  const valueColor = COLOR_MAP[color] ?? COLOR_MAP.default;

  return (
    <div
      className={`
        bg-white border border-slate-200 rounded-xl p-4 flex flex-col gap-1
        shadow-card hover:shadow-md transition-shadow duration-200
        ${className || ""}
      `}
    >
      <p className="text-slate-500 text-xs font-medium uppercase tracking-wide">
        {label}
      </p>
      <p className={`text-2xl font-bold ${valueColor} leading-tight`}>
        {value ?? "—"}
      </p>
      {sub && (
        <p className="text-slate-400 text-xs">{sub}</p>
      )}
    </div>
  );
};

StatBlock.propTypes = {
  label:     PropTypes.string.isRequired,
  value:     PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  sub:       PropTypes.string,
  color:     PropTypes.oneOf(["default", "blue", "green", "red", "amber", "purple"]),
  className: PropTypes.string,
};

StatBlock.defaultProps = {
  color: "default",
};

export default StatBlock;
