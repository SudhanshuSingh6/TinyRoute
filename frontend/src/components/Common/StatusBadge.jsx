import PropTypes from "prop-types";

const STATUS_CONFIG = {
  ACTIVE: {
    label: "Active",
    classes: "bg-green-100 text-green-700 border border-green-200",
    dot: "bg-green-500",
  },
  DISABLED: {
    label: "Disabled",
    classes: "bg-slate-100 text-slate-500 border border-slate-200",
    dot: "bg-slate-400",
  },
  EXPIRED: {
    label: "Expired",
    classes: "bg-amber-100 text-amber-700 border border-amber-200",
    dot: "bg-amber-500",
  },
  CLICK_LIMIT_REACHED: {
    label: "Limit reached",
    classes: "bg-red-100 text-red-600 border border-red-200",
    dot: "bg-red-500",
  },
};

const StatusBadge = ({ status }) => {
  const config = STATUS_CONFIG[status] ?? STATUS_CONFIG.DISABLED;

  return (
    <span
      className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold ${config.classes}`}
    >
      <span className={`w-1.5 h-1.5 rounded-full ${config.dot}`} />
      {config.label}
    </span>
  );
};

StatusBadge.propTypes = {
  status: PropTypes.oneOf([
    "ACTIVE",
    "DISABLED",
    "EXPIRED",
    "CLICK_LIMIT_REACHED",
  ]).isRequired,
};

export default StatusBadge;
