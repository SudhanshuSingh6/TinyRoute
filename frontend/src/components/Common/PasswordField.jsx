import { useState } from "react";
import PropTypes from "prop-types";
import { FaEye, FaEyeSlash } from "react-icons/fa";

const PasswordField = ({
  id,
  label,
  placeholder,
  autoComplete,
  disabled,
  register,
  errors,
  registerOptions,
}) => {
  const [show, setShow] = useState(false);
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="font-semibold text-md">
        {label}
      </label>
      <div className="relative">
        <input
          id={id}
          type={show ? "text" : "password"}
          placeholder={placeholder}
          autoComplete={autoComplete}
          disabled={disabled}
          className={`w-full rounded-md border bg-transparent px-2 py-2 pr-10 text-slate-700 outline-none
            ${errors[id]?.message ? "border-red-500" : "border-slate-600"}
            ${disabled ? "cursor-not-allowed bg-slate-100 opacity-50" : ""}
          `}
          {...register(id, registerOptions)}
        />
        <button
          type="button"
          tabIndex={-1}
          aria-label={show ? "Hide password" : "Show password"}
          onClick={() => setShow((prev) => !prev)}
          className="absolute top-1/2 right-2 -translate-y-1/2 text-slate-400 transition-colors hover:text-slate-700"
        >
          {show ? <FaEyeSlash /> : <FaEye />}
        </button>
      </div>
      {errors[id]?.message && (
        <p className="text-sm font-semibold text-red-600">
          {errors[id].message}
        </p>
      )}
    </div>
  );
};

PasswordField.propTypes = {
  id: PropTypes.string.isRequired,
  label: PropTypes.string.isRequired,
  placeholder: PropTypes.string,
  autoComplete: PropTypes.string,
  disabled: PropTypes.bool,
  register: PropTypes.func.isRequired,
  errors: PropTypes.object.isRequired,
  registerOptions: PropTypes.object,
};

PasswordField.defaultProps = {
  disabled: false,
  registerOptions: {},
};

export default PasswordField;
