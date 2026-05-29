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

const RegisterPage = () => {
  const navigate = useNavigate();
  const [loader, setLoader] = useState(false);

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
      await api.post(API.REGISTER, {
        username: data.username,
        email: data.email,
        password: data.password,
      });
      reset();
      toast.success("Registration successful! Please log in.");
      navigate("/login");
    } catch (error) {
      handleApiError(error, {}, "Registration failed. Please try again.");
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

          <PasswordField
            id="password"
            label="Password"
            placeholder="Create a password (min 6 chars)"
            disabled={loader}
            register={register}
            errors={errors}
            registerOptions={{
              required: { value: true, message: "*Password is required" },
              minLength: { value: 6, message: "Minimum 6 characters required" },
            }}
          />

          <PasswordField
            id="confirmPassword"
            label="Confirm Password"
            placeholder="Re-enter your password"
            disabled={loader}
            register={register}
            errors={errors}
            registerOptions={{
              required: { value: true, message: "*Please confirm your password" },
              validate: (value) => value === password || "Passwords do not match",
            }}
          />
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
