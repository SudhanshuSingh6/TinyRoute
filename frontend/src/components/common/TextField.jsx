import PropTypes from "prop-types";

const TextField = ({
  label,
  id,
  type,
  errors,
  register,
  required,
  message,
  className,
  min,
  placeholder,
  disabled,
}) => {
  return (
    <div className="flex flex-col gap-1">
      <label
        htmlFor={id}
        className={`${className ? className : ""} font-semibold text-md`}
      >
        {label}
      </label>

      <input
        type={type}
        id={id}
        placeholder={placeholder}
        disabled={disabled}
        className={`${className ? className : ""}
          px-2 py-2 border outline-none bg-transparent text-slate-700 rounded-md
          ${errors[id]?.message ? "border-red-500" : "border-slate-600"}
          ${disabled ? "opacity-50 cursor-not-allowed bg-slate-100" : ""}
        `}
        {...register(id, {
          required: { value: required, message },
          minLength: min
            ? { value: min, message: "Minimum 6 characters required" }
            : null,
          pattern:
            type === "email"
              ? {
                  value: /^[a-zA-Z0-9]+@(?:[a-zA-Z0-9]+\.)+com+$/,
                  message: "Invalid email",
                }
              : type === "url"
              ? {
                  value:
                    /^(https?:\/\/)?(([a-zA-Z0-9\u00a1-\uffff-]+\.)+[a-zA-Z\u00a1-\uffff]{2,})(:\d{2,5})?(\/[^\s]*)?$/,
                  message: "Please enter a valid URL",
                }
              : null,
        })}
      />

      {errors[id]?.message && (
        <p className="text-sm font-semibold text-red-600 mt-0">
          {errors[id]?.message}*
        </p>
      )}
    </div>
  );
};

TextField.propTypes = {
  label: PropTypes.string.isRequired,
  id: PropTypes.string.isRequired,
  type: PropTypes.string.isRequired,
  errors: PropTypes.object.isRequired,
  register: PropTypes.func.isRequired,
  required: PropTypes.bool,
  message: PropTypes.string,
  className: PropTypes.string,
  min: PropTypes.number,
  placeholder: PropTypes.string,
  disabled: PropTypes.bool,
};

TextField.defaultProps = {
  required: false,
  disabled: false,
};

export default TextField;
