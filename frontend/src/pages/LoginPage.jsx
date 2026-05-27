import { API } from "../utils/apiRoutes";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate } from "react-router-dom";
import toast from "react-hot-toast";
import { FaEye, FaEyeSlash } from "react-icons/fa";
import TextField from "../components/Common/TextField";
import Button from "../components/Common/Button";
import api from "../api/api";

const LoginPage = () => {
  const navigate = useNavigate();

  const [loader, setLoader] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm({
    defaultValues: {
      username: "",
      password: "",
    },
    mode: "onTouched",
  });

  const loginHandler = async (data) => {
    if (loader) return;

    setLoader(true);

    try {
      await api.post(API.LOGIN, data);

      toast.success("Login successful!");

      reset();

      navigate("/dashboard", { replace: true });
    } catch (error) {
      const status = error?.response?.status;

      if (status === 401) {
        toast.error("Incorrect username or password.");
      } else if (status === 404) {
        toast.error("User not found.");
      } else {
        const message =
          error?.response?.data?.message || "Login failed. Please try again.";

        toast.error(message);
      }
    } finally {
      setLoader(false);
    }
  };

  return (
    <div className="min-h-page flex items-center justify-center bg-gray-50">
      <form
        onSubmit={handleSubmit(loginHandler)}
        className="sm:w-form-md w-form-sm rounded-xl bg-white py-8 sm:px-8 px-4 shadow-custom"
      >
        <h1 className="text-center font-montserrat lg:text-3xl text-2xl font-bold text-btnColor">
          Welcome Back
        </h1>

        <p className="mt-1 mb-5 text-center text-sm text-slate-500">
          Log in to manage your links
        </p>

        <hr className="mb-5 border-slate-200" />

        <div className="flex flex-col gap-4">
          <TextField
            label="Username"
            required
            id="username"
            type="text"
            message="*Username is required"
            placeholder="Enter your username"
            register={register}
            errors={errors}
            disabled={loader}
            autoComplete="username"
          />

          <div className="flex flex-col gap-1">
            <label htmlFor="password" className="text-md font-semibold">
              Password
            </label>

            <div className="relative">
              <input
                id="password"
                type={showPassword ? "text" : "password"}
                placeholder="Enter your password"
                autoComplete="current-password"
                disabled={loader}
                className={`w-full rounded-md border bg-transparent px-2 py-2 pr-10 text-slate-700 outline-none
                  ${
                    errors.password?.message
                      ? "border-red-500"
                      : "border-slate-600"
                  }
                  ${loader ? "cursor-not-allowed bg-slate-100 opacity-50" : ""}
                `}
                {...register("password", {
                  required: {
                    value: true,
                    message: "*Password is required",
                  },
                  minLength: {
                    value: 6,
                    message: "Minimum 6 characters required",
                  },
                })}
              />

              <button
                type="button"
                aria-label={showPassword ? "Hide password" : "Show password"}
                onClick={() => setShowPassword((prev) => !prev)}
                className="absolute top-1/2 right-2 -translate-y-1/2 text-slate-400 transition-colors hover:text-slate-700"
              >
                {showPassword ? <FaEyeSlash /> : <FaEye />}
              </button>
            </div>

            {errors.password?.message && (
              <p className="text-sm font-semibold text-red-600">
                {errors.password.message}
              </p>
            )}
          </div>
        </div>

        <Button
          type="submit"
          variant="primary"
          size="md"
          loading={loader}
          disabled={loader}
          fullWidth
          className="mt-5"
        >
          Login
        </Button>

        <p className="mt-6 text-center text-sm text-slate-700">
          Don&apos;t have an account?{" "}
          <Link
            to="/register"
            className="font-semibold text-btnColor underline hover:opacity-80"
          >
            Sign Up
          </Link>
        </p>
      </form>
    </div>
  );
};

export default LoginPage;
