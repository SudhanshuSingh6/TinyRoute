import PropTypes from "prop-types";

const Card = ({ title, desc, icon }) => {
  return (
    <div className="border border-slate-200 bg-white flex flex-col px-6 py-8 gap-3 rounded-xl shadow-md hover:shadow-lg hover:-translate-y-1 transition-all duration-300">
      {icon && (
        <div className="w-10 h-10 rounded-lg bg-custom-gradient flex items-center justify-center text-white text-xl mb-1">
          {icon}
        </div>
      )}
      <h1 className="text-slate-900 text-lg font-bold">{title}</h1>
      <p className="text-slate-600 text-sm leading-relaxed">{desc}</p>
    </div>
  );
};

Card.propTypes = {
  title: PropTypes.string.isRequired,
  desc: PropTypes.string.isRequired,
  icon: PropTypes.node,
};

export default Card;
