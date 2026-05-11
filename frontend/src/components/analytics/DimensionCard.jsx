import PropTypes from "prop-types";
import DimensionBar from "./DimensionBar";

/**
 * Analytics breakdown card.
 * Receives an array of { key, count } items (already sorted desc by count).
 * Renders up to `limit` rows with a gradient progress bar per row.
 */
const DimensionCard = ({ title, icon, data, limit }) => {
  const sliced = data.slice(0, limit);
  const total = data.reduce((sum, d) => sum + d.count, 0);

  return (
    <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-card">
      {/* Header */}
      <div className="flex items-center gap-2 mb-4">
        {icon && (
          <div className="w-8 h-8 rounded-lg bg-custom-gradient flex items-center justify-center text-white text-sm shrink-0">
            {icon}
          </div>
        )}
        <h3 className="text-sm font-bold text-slate-700 uppercase tracking-wide">
          {title}
        </h3>
      </div>

      {/* Rows */}
      {sliced.length === 0 ? (
        <p className="text-slate-400 text-sm">No data for this range.</p>
      ) : (
        <div className="flex flex-col gap-3">
          {sliced.map((d) => (
            <DimensionBar key={d.key} label={d.key} count={d.count} total={total} />
          ))}
        </div>
      )}
    </div>
  );
};

DimensionCard.propTypes = {
  title: PropTypes.string.isRequired,
  /** Emoji or text icon shown in the gradient square */
  icon: PropTypes.node,
  /** Array of { key: string, count: number } sorted descending */
  data: PropTypes.arrayOf(
    PropTypes.shape({ key: PropTypes.string, count: PropTypes.number })
  ).isRequired,
  limit: PropTypes.number,
};

DimensionCard.defaultProps = {
  icon: null,
  limit: 5,
};

export default DimensionCard;