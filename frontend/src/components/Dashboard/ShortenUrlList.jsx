import ShortenItem from "./ShortenItem";
import PropTypes from "prop-types";

const ShortenUrlList = ({ data, refetch }) => {
  return (
    <div className="my-6 space-y-4">
      {data.map((item) => (
        <ShortenItem key={item.id} {...item} refetch={refetch} />
      ))}
    </div>
  );
};

ShortenUrlList.propTypes = {
  data: PropTypes.array.isRequired,
  refetch: PropTypes.func,
};

export default ShortenUrlList;
