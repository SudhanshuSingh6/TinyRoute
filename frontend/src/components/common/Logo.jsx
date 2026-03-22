import PropTypes from "prop-types";

const SIZE_MAP = {
  sm: { icon: 28, text: "text-xl" },
  md: { icon: 36, text: "text-2xl" },
  lg: { icon: 48, text: "text-4xl" },
};

const LogoIcon = ({ size }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 40 40"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
  >
    <defs>
      <linearGradient
        id="logo-gradient"
        x1="0" y1="0" x2="40" y2="40"
        gradientUnits="userSpaceOnUse"
      >
        <stop offset="0%" stopColor="#10b981" />
        <stop offset="100%" stopColor="#3b82f6" />
      </linearGradient>
    </defs>

    {/* Rounded square background */}
    <rect width="40" height="40" rx="10" fill="url(#logo-gradient)" />

    {/* S-curve route path */}
    <path
      d="M10 26 C10 26 14 14 20 20 C26 26 30 14 30 14"
      stroke="white"
      strokeWidth="2.5"
      strokeLinecap="round"
      fill="none"
    />

    {/* Start node */}
    <circle cx="10" cy="26" r="3" fill="white" />

    {/* End node */}
    <circle cx="30" cy="14" r="3" fill="white" />

    {/* Mid node (waypoint) */}
    <circle cx="20" cy="20" r="2" fill="white" opacity="0.6" />
  </svg>
);

LogoIcon.propTypes = {
  size: PropTypes.number.isRequired,
};

const Logo = ({ size, variant, iconOnly }) => {
  const { icon: iconSize, text: textClass } = SIZE_MAP[size] || SIZE_MAP.md;
  const textColor = variant === "light" ? "text-white" : "text-slate-800";

  return (
    <div className="flex items-center gap-2 select-none">
      <LogoIcon size={iconSize} />
      {!iconOnly && (
        <span
          className={`font-semibold tracking-widest uppercase ${textClass} ${textColor} font-montserrat`}
        >
          TINY<span className="font-light">ROUTE</span>
        </span>
      )}
    </div>
  );
};

Logo.propTypes = {
  size: PropTypes.oneOf(["sm", "md", "lg"]),
  variant: PropTypes.oneOf(["light", "dark"]),
  iconOnly: PropTypes.bool,
};

Logo.defaultProps = {
  size: "md",
  variant: "light",
  iconOnly: false,
};

export default Logo;