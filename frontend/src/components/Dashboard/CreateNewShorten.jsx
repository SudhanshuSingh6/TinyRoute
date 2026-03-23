import { API } from "../../utils/apiRoutes";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Tooltip } from "@mui/material";
import { RxCross2 } from "react-icons/rx";
import toast from "react-hot-toast";
import TextField from "../common/TextField";
import Button from "../common/Button";
import api from "../../api/api";
import { useStoreContext } from "../../contextApi/ContextApi";

const CreateNewShorten = ({ setOpen, refetch }) => {
  const { token } = useStoreContext();
  const [loading, setLoading] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm({
    defaultValues: { originalUrl: "" },
    mode: "onTouched",
  });

  const createShortUrlHandler = async (data) => {
    setLoading(true);
    try {
      const { data: res } = await api.post(API.SHORTEN, data, {
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
          Authorization: "Bearer " + token,
        },
      });

      const shortenUrl = `${import.meta.env.VITE_REACT_SUBDOMAIN}/${res.shortUrl}`;

      await navigator.clipboard.writeText(shortenUrl);
      toast.success("Short URL created & copied to clipboard!", {
        duration: 3000,
      });

      reset();
      await refetch();
      setOpen(false);
    } catch (error) {
      toast.error("Failed to create short URL. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex justify-center items-center bg-white rounded-xl">
      <form
        onSubmit={handleSubmit(createShortUrlHandler)}
        className="sm:w-form-md w-form-sm relative shadow-custom pt-8 pb-5 sm:px-8 px-4 rounded-xl"
      >
        <h1 className="font-montserrat sm:mt-0 mt-3 text-center font-bold sm:text-2xl text-22 text-slate-800">
          Create Short URL
        </h1>

        <hr className="mt-2 sm:mb-5 mb-3 border-slate-200" />

        <div className="mb-1">
          <TextField
            label="Enter URL"
            required
            id="originalUrl"
            placeholder="https://example.com/your/long/url"
            type="url"
            message="URL is required"
            register={register}
            errors={errors}
            disabled={loading}
          />
        </div>

        {/* FIXED: was type="text", now type="submit" */}
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
