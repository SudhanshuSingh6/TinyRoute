import PropTypes from "prop-types";

const today = () => new Date().toISOString().split("T")[0];
const daysAgo = (n) => {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return d.toISOString().split("T")[0];
};

const DateRangePicker = ({
  startDate,
  endDate,
  onChange,
  type,
  label,
  disabled,
}) => {
  const handleStart = (e) => onChange(e.target.value, endDate);
  const handleEnd   = (e) => onChange(startDate, e.target.value);

  const inputClass = `
    border border-slate-300 rounded-md px-3 py-1.5 text-sm text-slate-700
    outline-none focus:border-btnColor focus:ring-1 focus:ring-btnColor
    bg-white disabled:opacity-50 disabled:cursor-not-allowed
    transition-colors duration-150
  `;

  return (
    <div className="flex flex-wrap items-center gap-3">
      {label && (
        <span className="text-slate-500 text-sm font-medium">{label}</span>
      )}
      <div className="flex items-center gap-2">
        <input
          type={type}
          value={startDate}
          onChange={handleStart}
          disabled={disabled}
          max={endDate}
          className={inputClass}
          aria-label="Start date"
        />
        <span className="text-slate-400 text-sm">to</span>
        <input
          type={type}
          value={endDate}
          onChange={handleEnd}
          disabled={disabled}
          min={startDate}
          max={today()}
          className={inputClass}
          aria-label="End date"
        />
      </div>
    </div>
  );
};

DateRangePicker.propTypes = {
  startDate: PropTypes.string.isRequired,
  endDate:   PropTypes.string.isRequired,
  onChange:  PropTypes.func.isRequired,
  type:      PropTypes.oneOf(["date", "datetime-local"]),
  label:     PropTypes.string,
  disabled:  PropTypes.bool,
};

DateRangePicker.defaultProps = {
  type:     "date",
  disabled: false,
};

// Export helpers so parent components can use same defaults
export { today, daysAgo };
export default DateRangePicker;
