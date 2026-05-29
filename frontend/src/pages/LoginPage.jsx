import { API } from "../utils/apiRoutes";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate } from "react-router-dom";
import toast from "react-hot-toast";
import TextField from "../components/Common/TextField";
import Button from "../components/Common/Button";
import PasswordField from "../components/Common/PasswordField";
import api from "../api/api";
import { handleApiError } from "../utils/errorHandler";

const LoginPage = () => {
  const navigate = useNavigate();

  const [loader, setLoader] = useState(false);

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
      handleApiError(error, {}, "Login failed. Please try again.");
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

          <PasswordField
            id="password"
            label="Password"
            placeholder="Enter your password"
            autoComplete="current-password"
            disabled={loader}
            register={register}
            errors={errors}
            registerOptions={{
              required: { value: true, message: "*Password is required" },
              minLength: { value: 6, message: "Minimum 6 characters required" },
            }}
          />
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
