import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate } from "react-router-dom";
import toast from "react-hot-toast";
import { FaEye, FaEyeSlash } from "react-icons/fa";
import TextField from "../components/common/TextField";
import Button from "../components/common/Button";
import api from "../api/api";

const RegisterPage = () => {
  const navigate = useNavigate();
  const [loader, setLoader] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    watch,
    formState: { errors },
  } = useForm({
    defaultValues: { username: "", email: "", password: "", confirmPassword: "" },
    mode: "onTouched",
  });

  const password = watch("password");

  const registerHandler = async (data) => {
    setLoader(true);
    try {
      await api.post("/api/auth/public/register", {
        username: data.username,
        email: data.email,
        password: data.password,
      });
      reset();
      toast.success("Registration successful! Please log in.");
      navigate("/login");
    } catch (error) {
      const status = error?.response?.status;
      if (status === 409) {
        toast.error("Username or email already exists.");
      } else {
        toast.error("Registration failed. Please try again.");
      }
    } finally {
      setLoader(false);
    }
  };

  return (
    <div className="min-h-page flex justify-center items-center bg-gray-50">
      <form
        onSubmit={handleSubmit(registerHandler)}
        className="sm:w-form-md w-form-sm bg-white shadow-custom py-8 sm:px-8 px-4 rounded-xl"
      >
        <h1 className="text-center font-montserrat text-btnColor font-bold lg:text-3xl text-2xl">
          Create Account
        </h1>
        <p className="text-center text-slate-500 text-sm mt-1 mb-5">
          Start shortening links for free
        </p>

        <hr className="mb-5 border-slate-200" />

        <div className="flex flex-col gap-4">
          <TextField
            label="Username"
            required
            id="username"
            type="text"
            message="*Username is required"
            placeholder="Choose a username"
            register={register}
            errors={errors}
            disabled={loader}
          />

          <TextField
            label="Email"
            required
            id="email"
            type="email"
            message="*Email is required"
            placeholder="Enter your email"
            register={register}
            errors={errors}
            disabled={loader}
          />

          {/* Password */}
          <div className="flex flex-col gap-1">
            <label className="font-semibold text-md">Password</label>
            <div className="relative">
              <input
                type={showPassword ? "text" : "password"}
                placeholder="Create a password (min 6 chars)"
                disabled={loader}
                className={`w-full px-2 py-2 border outline-none bg-transparent text-slate-700 rounded-md pr-10
                  ${errors.password?.message ? "border-red-500" : "border-slate-600"}
                  ${loader ? "opacity-50 cursor-not-allowed bg-slate-100" : ""}
                `}
                {...register("password", {
                  required: { value: true, message: "*Password is required" },
                  minLength: { value: 6, message: "Minimum 6 characters required" },
                })}
              />
              <button
                type="button"
                tabIndex={-1}
                onClick={() => setShowPassword(!showPassword)}
                className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-700"
              >
                {showPassword ? <FaEyeSlash /> : <FaEye />}
              </button>
            </div>
            {errors.password?.message && (
              <p className="text-sm font-semibold text-red-600">
                {errors.password.message}*
              </p>
            )}
          </div>

          {/* Confirm Password */}
          <div className="flex flex-col gap-1">
            <label className="font-semibold text-md">Confirm Password</label>
            <div className="relative">
              <input
                type={showConfirm ? "text" : "password"}
                placeholder="Re-enter your password"
                disabled={loader}
                className={`w-full px-2 py-2 border outline-none bg-transparent text-slate-700 rounded-md pr-10
                  ${errors.confirmPassword?.message ? "border-red-500" : "border-slate-600"}
                  ${loader ? "opacity-50 cursor-not-allowed bg-slate-100" : ""}
                `}
                {...register("confirmPassword", {
                  required: { value: true, message: "*Please confirm your password" },
                  validate: (value) =>
                    value === password || "Passwords do not match",
                })}
              />
              <button
                type="button"
                tabIndex={-1}
                onClick={() => setShowConfirm(!showConfirm)}
                className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-700"
              >
                {showConfirm ? <FaEyeSlash /> : <FaEye />}
              </button>
            </div>
            {errors.confirmPassword?.message && (
              <p className="text-sm font-semibold text-red-600">
                {errors.confirmPassword.message}*
              </p>
            )}
          </div>
        </div>

        <Button
          type="submit"
          variant="primary"
          size="md"
          loading={loader}
          fullWidth
          className="mt-5"
        >
          Register
        </Button>

        <p className="text-center text-sm text-slate-700 mt-6">
          Already have an account?{" "}
          <Link
            to="/login"
            className="text-btnColor font-semibold underline hover:opacity-80"
          >
            Login
          </Link>
        </p>
      </form>
    </div>
  );
};

export default RegisterPage;
