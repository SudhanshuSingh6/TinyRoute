import { useState } from "react";
import { useForm } from "react-hook-form";
import toast from "react-hot-toast";
import { createShortUrl } from "./useQuery";
import { handleApiError } from "../utils/errorHandler";

const DEFAULT_VALUES = {
  originalUrl: "",
  customAlias: "",
  title: "",
  expiresAt: "",
};

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
  };

  return Object.fromEntries(
    Object.entries(payload).filter(
      ([, value]) => value !== undefined && value !== null && value !== "",
    ),
  );
};

export function useShortenForm({ onSuccess } = {}) {
  const [loading, setLoading] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm({ defaultValues: DEFAULT_VALUES, mode: "onTouched" });

  const onSubmit = handleSubmit(async (values) => {
    setLoading(true);
    try {
      const payload = cleanPayload(values);
      const res = await createShortUrl(payload);
      const shortenUrl = `${import.meta.env.VITE_REACT_SUBDOMAIN}/${res.shortUrl}`;
      await navigator.clipboard.writeText(shortenUrl);
      toast.success("Short URL created & copied to clipboard!", { duration: 3000 });
      reset(DEFAULT_VALUES);
      await onSuccess?.();
    } catch (error) {
      handleApiError(error, {}, "Failed to create short URL right now.");
    } finally {
      setLoading(false);
    }
  });

  return { register, errors, onSubmit, loading };
}
