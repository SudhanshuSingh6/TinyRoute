import { useState } from "react";
import { useForm } from "react-hook-form";
import { Tooltip } from "@mui/material";
import { FaChevronDown, FaChevronUp } from "react-icons/fa";
import { RxCross2 } from "react-icons/rx";
import toast from "react-hot-toast";
import Button from "../common/Button";
import { useStoreContext } from "../../contextApi/ContextApi";
import { createShortUrl } from "../../hooks/useQuery";

const aliasRegex = /^[a-zA-Z0-9_-]{3,32}$/;

const CreateNewShorten = ({ setOpen, refetch }) => {
  const { token } = useStoreContext();
  const [loading, setLoading] = useState(false);
  const [showAdvanced, setShowAdvanced] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm({
    defaultValues: {
      originalUrl: "",
      customAlias: "",
      title: "",
      expiresAt: "",
      maxClicks: "",
      isPublic: true,
    },
    mode: "onTouched",
  });

  const cleanPayload = (values) => {
    const normalizedExpiresAt = values.expiresAt
      ? values.expiresAt.length === 16
        ? `${values.expiresAt}:00`
        : values.expiresAt
      : undefined;

    const payload = {
      originalUrl: values.originalUrl.trim(),
      customAlias: values.customAlias?.trim() || undefined,
      title: values.title?.trim() || undefined,
      expiresAt: normalizedExpiresAt,
      maxClicks: Number.isFinite(values.maxClicks) ? values.maxClicks : undefined,
      isPublic: Boolean(values.isPublic),
    };

    return Object.fromEntries(
      Object.entries(payload).filter(
        ([, value]) => value !== undefined && value !== null && value !== ""
      )
    );
  };

  const createShortUrlHandler = async (values) => {
    setLoading(true);

    try {
      const payload = cleanPayload(values);
      const res = await createShortUrl(token, payload);
      const shortenUrl = `${import.meta.env.VITE_REACT_SUBDOMAIN}/${res.shortUrl}`;

      await navigator.clipboard.writeText(shortenUrl);

      toast.success("Short URL created & copied to clipboard!", {
        duration: 3000,
      });

      reset({
        originalUrl: "",
        customAlias: "",
        title: "",
        expiresAt: "",
        maxClicks: "",
        isPublic: true,
      });

      await refetch();
      setOpen(false);
    } catch (error) {
      const status = error?.response?.status;
      if (status === 400) {
        toast.error("Please check your URL and advanced options.");
      } else if (status === 409) {
        toast.error("Custom alias is already taken.");
      } else if (status === 429) {
        toast.error("Rate limit reached. Please try again shortly.");
      } else {
        toast.error("Failed to create short URL right now.");
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex justify-center items-center bg-white rounded-xl">
      <form
        onSubmit={handleSubmit(createShortUrlHandler)}
        className="sm:w-form-md w-form-sm relative shadow-custom pt-8 pb-5 sm:px-8 px-4 rounded-xl max-h-[90vh] overflow-y-auto"
      >
        <h1 className="font-montserrat sm:mt-0 mt-3 text-center font-bold sm:text-2xl text-22 text-slate-800">
          Create Short URL
        </h1>

        <hr className="mt-2 sm:mb-5 mb-3 border-slate-200" />

        <div className="mb-1">
          <label className="font-semibold text-md">Enter URL</label>
          <input
            type="url"
            placeholder="https://example.com/your/long/url"
            disabled={loading}
            className={`w-full mt-1 px-2 py-2 border outline-none bg-transparent text-slate-700 rounded-md ${
              errors.originalUrl ? "border-red-500" : "border-slate-600"
            } ${loading ? "opacity-50 cursor-not-allowed bg-slate-100" : ""}`}
            {...register("originalUrl", {
              required: "URL is required",
              validate: (value) => {
                try {
                  const url = new URL(value);
                  return (
                    url.protocol === "http:" ||
                    url.protocol === "https:" ||
                    "Please enter a valid URL"
                  );
                } catch {
                  return "Please enter a valid URL";
                }
              },
            })}
          />
          {errors.originalUrl && (
            <p className="text-sm font-semibold text-red-600 mt-1">
              {errors.originalUrl.message}
            </p>
          )}
        </div>

        <button
          type="button"
          onClick={() => setShowAdvanced((prev) => !prev)}
          className="mt-4 w-full flex items-center justify-between border border-slate-200 rounded-md px-3 py-2 text-slate-700 hover:bg-slate-50 transition-colors"
        >
          <span className="font-semibold text-sm">Show advanced options</span>
          {showAdvanced ? <FaChevronUp /> : <FaChevronDown />}
        </button>

        {showAdvanced && (
          <div className="mt-4 grid gap-3">
            <div>
              <label className="font-semibold text-sm">Custom Alias (optional)</label>
              <input
                type="text"
                placeholder="my-awesome-link"
                disabled={loading}
                className={`w-full mt-1 px-2 py-2 border rounded-md outline-none ${
                  errors.customAlias ? "border-red-500" : "border-slate-300"
                }`}
                {...register("customAlias", {
                  validate: (value) =>
                    !value ||
                    aliasRegex.test(value) ||
                    "Use 3-32 chars: letters, numbers, _ or -",
                })}
              />
              {errors.customAlias && (
                <p className="text-sm font-semibold text-red-600 mt-1">
                  {errors.customAlias.message}
                </p>
              )}
            </div>

            <div>
              <label className="font-semibold text-sm">Title (optional)</label>
              <input
                type="text"
                placeholder="Campaign landing page"
                disabled={loading}
                className={`w-full mt-1 px-2 py-2 border rounded-md outline-none ${
                  errors.title ? "border-red-500" : "border-slate-300"
                }`}
                {...register("title", {
                  maxLength: {
                    value: 120,
                    message: "Title must be 120 characters or less",
                  },
                })}
              />
              {errors.title && (
                <p className="text-sm font-semibold text-red-600 mt-1">
                  {errors.title.message}
                </p>
              )}
            </div>

            <div>
              <label className="font-semibold text-sm">Expires At (optional)</label>
              <input
                type="datetime-local"
                disabled={loading}
                className={`w-full mt-1 px-2 py-2 border rounded-md outline-none ${
                  errors.expiresAt ? "border-red-500" : "border-slate-300"
                }`}
                {...register("expiresAt", {
                  validate: (value) =>
                    !value ||
                    new Date(value).getTime() > Date.now() ||
                    "Expiry must be in the future",
                })}
              />
              {errors.expiresAt && (
                <p className="text-sm font-semibold text-red-600 mt-1">
                  {errors.expiresAt.message}
                </p>
              )}
            </div>

            <div>
              <label className="font-semibold text-sm">Max Clicks (optional)</label>
              <input
                type="number"
                min={1}
                disabled={loading}
                className={`w-full mt-1 px-2 py-2 border rounded-md outline-none ${
                  errors.maxClicks ? "border-red-500" : "border-slate-300"
                }`}
                {...register("maxClicks", {
                  valueAsNumber: true,
                  validate: (value) =>
                    Number.isNaN(value) ||
                    value >= 1 ||
                    "Max clicks must be at least 1",
                })}
              />
              {errors.maxClicks && (
                <p className="text-sm font-semibold text-red-600 mt-1">
                  {errors.maxClicks.message}
                </p>
              )}
            </div>

            <label className="flex items-center gap-2 text-sm font-semibold text-slate-700">
              <input
                type="checkbox"
                disabled={loading}
                {...register("isPublic")}
              />
              Show on bio page
            </label>
          </div>
        )}

        <Button
          type="submit"
          variant="primary"
          size="md"
          loading={loading}
          className="mt-4"
        >
          Create & Copy
        </Button>

        {!loading && (
          <Tooltip title="Close">
            <button
              type="button"
              onClick={() => setOpen(false)}
              className="absolute right-2 top-2 text-slate-500 hover:text-slate-800"
            >
              <RxCross2 className="text-3xl" />
            </button>
          </Tooltip>
        )}
      </form>
    </div>
  );
};

export default CreateNewShorten;
