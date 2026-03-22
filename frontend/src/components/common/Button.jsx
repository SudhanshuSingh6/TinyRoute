import PropTypes from "prop-types";
import { ThreeDots } from "react-loader-spinner";

const VARIANTS = {
  primary:
    "bg-custom-gradient text-white hover:opacity-90",
  secondary:
    "border border-btnColor text-btnColor bg-transparent hover:bg-blue-50 transition-colors duration-150",
  danger:
    "bg-rose-600 text-white hover:bg-rose-700",
  ghost:
    "bg-transparent text-slate-700 hover:bg-slate-100",
};

const SIZES = {
  sm: "px-3 py-1.5 text-sm",
  md: "px-5 py-2 text-sm",
  lg: "px-7 py-3 text-base",
};

const Button = ({
  children,
  variant,
  size,
  loading,
  disabled,
  onClick,
  type,
  className,
  fullWidth,
}) => {
  const isDisabled = disabled || loading;

  return (
    <button
      type={type}
      onClick={onClick}
      disabled={isDisabled}
      className={`
        ${VARIANTS[variant]}
        ${SIZES[size]}
        ${fullWidth ? "w-full" : ""}
        ${isDisabled ? "opacity-60 cursor-not-allowed" : "cursor-pointer"}
        font-semibold rounded-md transition-all duration-200 flex items-center justify-center gap-2
        ${className || ""}
      `}
    >
      {loading ? (
        <>
          <ThreeDots
            height="18"
            width="28"
            color="#ffffff"
            visible={true}
          />
          <span>Loading...</span>
        </>
      ) : (
        children
      )}
    </button>
  );
};

Button.propTypes = {
  children: PropTypes.node.isRequired,
  variant: PropTypes.oneOf(["primary", "secondary", "danger", "ghost"]),
  size: PropTypes.oneOf(["sm", "md", "lg"]),
  loading: PropTypes.bool,
  disabled: PropTypes.bool,
  onClick: PropTypes.func,
  type: PropTypes.oneOf(["button", "submit", "reset"]),
  className: PropTypes.string,
  fullWidth: PropTypes.bool,
};

Button.defaultProps = {
  variant: "primary",
  size: "md",
  loading: false,
  disabled: false,
  type: "button",
  fullWidth: false,
};

export default Button;