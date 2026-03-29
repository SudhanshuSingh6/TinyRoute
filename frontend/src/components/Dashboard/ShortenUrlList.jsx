import PropTypes from "prop-types";
import ShortenItem from "./ShortenItem";

const ShortenUrlList = ({ data, refetch }) => {
  return (
    <div className="my-6 space-y-4">
      {data.map((item) => (
        <ShortenItem
          key={item.id}
          id={item.id}
          originalUrl={item.originalUrl}
          shortUrl={item.shortUrl}
          clickCount={item.clickCount}
          createdDate={item.createdDate}
          status={item.status}
          title={item.title}
          expiresAt={item.expiresAt}
          maxClicks={item.maxClicks}
          refetch={refetch}
        />
      ))}
    </div>
  );
};

ShortenUrlList.propTypes = {
  data:    PropTypes.array.isRequired,
  refetch: PropTypes.func.isRequired,
};

export default ShortenUrlList;
