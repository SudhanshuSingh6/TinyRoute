import PropTypes from "prop-types";

const ErrorMessage = ({ message }) => {
  if (!message) return null;
  return <p className="text-sm font-semibold text-red-600 mt-1">{message}</p>;
};

ErrorMessage.propTypes = {
  message: PropTypes.string,
};

export default ErrorMessage;
