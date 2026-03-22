import PropTypes from "prop-types";
import { motion } from "framer-motion";
import Button from "./Button";

const EmptyState = ({ icon, title, subtitle, actionLabel, onAction }) => {
  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4 }}
      className="flex flex-col items-center justify-center py-16 px-6"
    >
      {icon && (
        <div className="w-16 h-16 rounded-2xl bg-slate-100 flex items-center justify-center text-slate-400 text-3xl mb-4">
          {icon}
        </div>
      )}
      <h2 className="text-slate-800 font-bold text-xl mb-2 text-center">
        {title}
      </h2>
      {subtitle && (
        <p className="text-slate-500 text-sm text-center max-w-sm mb-6">
          {subtitle}
        </p>
      )}
      {actionLabel && onAction && (
        <Button variant="primary" size="md" onClick={onAction}>
          {actionLabel}
        </Button>
      )}
    </motion.div>
  );
};

EmptyState.propTypes = {
  icon: PropTypes.node,
  title: PropTypes.string.isRequired,
  subtitle: PropTypes.string,
  actionLabel: PropTypes.string,
  onAction: PropTypes.func,
};

export default EmptyState;
