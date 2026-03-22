import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate } from "react-router-dom";
import toast from "react-hot-toast";
import { FaEye, FaEyeSlash } from "react-icons/fa";
import TextField from "../components/common/TextField";
import Button from "../components/common/Button";
import api from "../api/api";
import { useStoreContext } from "../contextApi/ContextApi";

const LoginPage = () => {
  const navigate = useNavigate();
  const [loader, setLoader] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const { setToken } = useStoreContext();

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm({
    defaultValues: { username: "", password: "" },
    mode: "onTouched",
  });

  const loginHandler = async (data) => {
    setLoader(true);
    try {
      const { data: response } = await api.post("/api/auth/public/login", data);
      setToken(response.token);
      localStorage.setItem("JWT_TOKEN", JSON.stringify(response.token));
      toast.success("Login successful!");
      reset();
      navigate("/dashboard");
    } catch (error) {
      const status = error?.response?.status;
      if (status === 401) {
        toast.error("Incorrect username or password.");
      } else if (status === 404) {
        toast.error("User not found.");
      } else {
        toast.error("Login failed. Please try again.");
      }
    } finally {
      setLoader(false);
    }
  };

  return (
    <div className="min-h-page flex justify-center items-center bg-gray-50">
      <form
        onSubmit={handleSubmit(loginHandler)}
        className="sm:w-form-md w-form-sm bg-white shadow-custom py-8 sm:px-8 px-4 rounded-xl"
      >
        <h1 className="text-center font-montserrat text-btnColor font-bold lg:text-3xl text-2xl">
          Welcome Back
        </h1>
        <p className="text-center text-slate-500 text-sm mt-1 mb-5">
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
          />

          {/* Password with show/hide toggle — custom since TextField doesn't support suffix icon */}
          <div className="flex flex-col gap-1">
            <label className="font-semibold text-md">Password</label>
            <div className="relative">
              <input
                type={showPassword ? "text" : "password"}
                placeholder="Enter your password"
                disabled={loader}
                className={`w-full px-2 py-2 border outline-none bg-transparent text-slate-700 rounded-md pr-10
                  ${errors.password?.message ? "border-red-500" : "border-slate-600"}
                  ${loader ? "opacity-50 cursor-not-allowed bg-slate-100" : ""}
                `}
                {...register("password", {
                  required: { value: true, message: "*Password is required" },
                  minLength: {
                    value: 6,
                    message: "Minimum 6 characters required",
                  },
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
        </div>

        <Button
          type="submit"
          variant="primary"
          size="md"
          loading={loader}
          fullWidth
          className="mt-5"
        >
          Login
        </Button>

        <p className="text-center text-sm text-slate-700 mt-6">
          Don&apos;t have an account?{" "}
          <Link
            to="/register"
            className="text-btnColor font-semibold underline hover:opacity-80"
          >
            Sign Up
          </Link>
        </p>
      </form>
    </div>
  );
};

export default LoginPage;
