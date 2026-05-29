import { useEffect, useState } from "react";
import PropTypes from "prop-types";
import { isValidHttpUrl } from "../../utils/helper";

const AvatarDisplay = ({ src, initials, size = 80, bordered = true }) => {
  const [errored, setErrored] = useState(false);

  useEffect(() => {
    setErrored(false);
  }, [src]);

  const show = src && isValidHttpUrl(src) && !errored;
  const style = {
    width: size,
    height: size,
    borderRadius: "50%",
    border: bordered ? "4px solid white" : "none",
    boxShadow: bordered ? "0 4px 14px rgba(0,0,0,0.12)" : "none",
    flexShrink: 0,
  };
  return show ? (
    <img
      src={src}
      alt={initials}
      onError={() => setErrored(true)}
      style={{ ...style, objectFit: "cover" }}
    />
  ) : (
    <div
      className="bg-custom-gradient flex items-center justify-center text-white font-bold"
      style={{ ...style, fontSize: size * 0.3 }}
    >
      {initials}
    </div>
  );
};

AvatarDisplay.propTypes = {
  src: PropTypes.string,
  initials: PropTypes.string.isRequired,
  size: PropTypes.number,
  bordered: PropTypes.bool,
};

export default AvatarDisplay;
