import PropTypes from "prop-types";

const ActionButton = ({ onClick, color, children, disabled }) => (
  <button
    type="button"
    onClick={onClick}
    disabled={disabled}
    className={`flex items-center gap-1 rounded-md px-4 py-2 font-semibold text-white shadow-md shadow-slate-500
      ${
        disabled
          ? "cursor-not-allowed opacity-40"
          : "cursor-pointer transition-opacity duration-150 hover:opacity-90"
      }
      ${color}
    `}
  >
    {children}
  </button>
);

ActionButton.propTypes = {
  onClick: PropTypes.func,
  color: PropTypes.string.isRequired,
  children: PropTypes.node.isRequired,
  disabled: PropTypes.bool,
};

export default ActionButton;
