import PropTypes from "prop-types";
import { MdOutlineTimer } from "react-icons/md";

/**
 * Highlighted card using the app's bg-custom-gradient.
 * Shows the peak activity time label and click count.
 */
const PeakActivityCard = ({ type,label, count }) => {
  return (
    <div
      className="rounded-xl p-6 flex flex-col items-center justify-center text-center gap-4 bg-custom-gradient"
    >
      {/* Icon bubble */}
      <div
        className="w-14 h-14 rounded-full flex items-center justify-center text-white text-2xl"
        style={{
          background: "rgba(255,255,255,0.15)",
          border: "1px solid rgba(255,255,255,0.25)",
        }}
      >
        <MdOutlineTimer />
      </div>

      {/* Label */}
      <p className="text-xs font-semibold uppercase tracking-widest text-blue-100">
        {type}
      </p>

      {/* Value */}
      <p className="text-4xl font-bold text-white font-montserrat leading-none">
        {label || "—"}
      </p>

      {/* Sub */}
      <p className="text-sm text-blue-100 leading-relaxed">
        Highest volume with{" "}
        <span className="font-bold text-white">{count} clicks</span> during
        this window.
      </p>
    </div>
  );
};

PeakActivityCard.propTypes = {
  /** Formatted peak time label, e.g. "19:00" */
  label: PropTypes.string,
  /** Click count during the peak bucket */
  count: PropTypes.number,
};

PeakActivityCard.defaultProps = {
  label: "—",
  count: 0,
};

export default PeakActivityCard;