import PropTypes from "prop-types";
import { Hourglass, ThreeDots, TailSpin } from "react-loader-spinner";

const SPINNERS = {
  hourglass: Hourglass,
  dots: ThreeDots,
  spin: TailSpin,
};

const Loader = ({ type, size, message, fullPage, color }) => {
  const SpinnerComponent = SPINNERS[type] || Hourglass;

  const spinnerProps =
    type === "dots"
      ? { height: size, width: size * 1.5, color }
      : { height: size, width: size, color };

  if (fullPage) {
    return (
      <div className="min-h-page flex justify-center items-center">
        <div className="flex flex-col items-center gap-3">
          <SpinnerComponent visible={true} ariaLabel="loading" {...spinnerProps} />
          {message && (
            <p className="text-slate-600 text-sm font-medium">{message}</p>
          )}
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col items-center justify-center gap-2 py-8">
      <SpinnerComponent visible={true} ariaLabel="loading" {...spinnerProps} />
      {message && (
        <p className="text-slate-600 text-sm font-medium">{message}</p>
      )}
    </div>
  );
};

Loader.propTypes = {
  type: PropTypes.oneOf(["hourglass", "dots", "spin"]),
  size: PropTypes.number,
  message: PropTypes.string,
  fullPage: PropTypes.bool,
  color: PropTypes.string,
};

Loader.defaultProps = {
  type: "hourglass",
  size: 50,
  fullPage: false,
  color: "#3b82f6",
};

export default Loader;
