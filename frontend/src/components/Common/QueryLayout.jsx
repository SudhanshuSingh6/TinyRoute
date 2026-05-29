import PropTypes from "prop-types";
import Loader from "./Loader";

const QueryLayout = ({ isLoading, loaderMessage, isEmpty, emptyState, children }) => {
  if (isLoading) return <Loader message={loaderMessage} />;
  if (isEmpty) return emptyState;
  return children;
};

QueryLayout.propTypes = {
  isLoading: PropTypes.bool.isRequired,
  loaderMessage: PropTypes.string,
  isEmpty: PropTypes.bool.isRequired,
  emptyState: PropTypes.node.isRequired,
  children: PropTypes.node.isRequired,
};

export default QueryLayout;
