import { useEffect, useMemo } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useForm } from "react-hook-form";
import toast from "react-hot-toast";
import Loader from "../components/common/Loader";
import Button from "../components/common/Button";
import StatBlock from "../components/common/StatBlock";
import { useStoreContext } from "../contextApi/ContextApi";
import { updateProfile, useFetchProfile } from "../hooks/useQuery";

const isValidHttpUrl = (value = "") => {
  try {
    const url = new URL(value);
    return url.protocol === "http:" || url.protocol === "https:";
  } catch {
    return false;
  }
};

const getInitials = (value = "") => {
  const parts = value.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "U";
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return `${parts[0][0]}${parts[1][0]}`.toUpperCase();
};

const ProfilePage = () => {
  const navigate = useNavigate();
  const { token, setToken } = useStoreContext();

  const handleProfileError = (error) => {
    const status = error?.response?.status;
    if (status === 401 || status === 403) {
      setToken(null);
      localStorage.removeItem("JWT_TOKEN");
      navigate("/login");
      return;
    }
    navigate("/error");
  };

  const { isLoading, data: profile, refetch } = useFetchProfile(
    token,
    handleProfileError
  );

  const {
    register,
    handleSubmit,
    reset,
    watch,
    formState: { errors, isSubmitting },
  } = useForm({
    defaultValues: { bio: "", avatarUrl: "" },
    mode: "onTouched",
  });

  useEffect(() => {
    if (profile) {
      reset({
        bio: profile.bio ?? "",
        avatarUrl: profile.avatarUrl ?? "",
      });
    }
  }, [profile, reset]);

  const username = profile?.username ?? "User";
  const bioViews =
    profile?.bioPageViews ?? profile?.bioViews ?? profile?.views ?? 0;

  const watchedAvatar = watch("avatarUrl");
  const avatarPreview = (watchedAvatar || profile?.avatarUrl || "").trim();
  const showAvatarImage = isValidHttpUrl(avatarPreview);

  const initials = useMemo(() => getInitials(username), [username]);

  const onSubmit = async (values) => {
    try {
      await updateProfile(token, {
        bio: values.bio?.trim() || "",
        avatarUrl: values.avatarUrl?.trim() || "",
      });
      toast.success("Profile updated.");
      await refetch();
    } catch (error) {
      const status = error?.response?.status;
      if (status === 400) {
        toast.error("Please check your bio and avatar URL.");
      } else if (status === 401 || status === 403) {
        setToken(null);
        localStorage.removeItem("JWT_TOKEN");
        navigate("/login");
      } else {
        toast.error("Could not update profile right now.");
      }
    }
  };

  if (isLoading) {
    return <Loader fullPage message="Loading profile..." />;
  }

  if (!profile) {
    return (
      <div className="min-h-page flex items-center justify-center px-4">
        <p className="text-slate-500">Profile data is not available right now.</p>
      </div>
    );
  }

  return (
    <div className="min-h-page lg:px-14 sm:px-8 px-4 py-10 bg-slate-50">
      <div className="max-w-5xl mx-auto space-y-6">
        <section className="bg-white border border-slate-200 rounded-xl p-6 shadow-card">
          <div className="flex flex-col sm:flex-row sm:items-center gap-6">
            {showAvatarImage ? (
              <img
                src={avatarPreview}
                alt={`${username} avatar`}
                className="w-20 h-20 rounded-full object-cover border border-slate-200"
                onError={(e) => {
                  e.currentTarget.style.display = "none";
                }}
              />
            ) : (
              <div className="w-20 h-20 rounded-full bg-custom-gradient text-white font-bold text-2xl flex items-center justify-center">
                {initials}
              </div>
            )}

            <div className="flex-1">
              <p className="text-xs uppercase tracking-wide text-slate-400 font-semibold">
                Username
              </p>
              <h1 className="text-2xl font-bold text-slate-900">{username}</h1>
              <p className="text-slate-600 mt-2">
                {profile.bio?.trim() || "No bio added yet."}
              </p>
              <Link
                to={`/bio/${username}`}
                target="_blank"
                rel="noreferrer"
                className="inline-flex items-center mt-3 text-sm text-btnColor font-semibold hover:underline"
              >
                View public bio page
              </Link>
            </div>

            <div className="w-full sm:w-52">
              <StatBlock label="Bio Page Views" value={bioViews} color="blue" />
            </div>
          </div>
        </section>

        <section className="bg-white border border-slate-200 rounded-xl p-6 shadow-card">
          <h2 className="text-xl font-bold text-slate-900 mb-4">Edit Profile</h2>

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div>
              <label className="font-semibold text-sm text-slate-700">Bio</label>
              <textarea
                rows={4}
                placeholder="Write a short bio..."
                className={`mt-1 w-full px-3 py-2 border rounded-md outline-none text-slate-700 ${
                  errors.bio ? "border-red-500" : "border-slate-300"
                }`}
                {...register("bio", {
                  maxLength: {
                    value: 280,
                    message: "Bio must be 280 characters or less.",
                  },
                })}
              />
              {errors.bio?.message && (
                <p className="text-sm text-red-600 mt-1">{errors.bio.message}</p>
              )}
            </div>

            <div className="grid md:grid-cols-[1fr,220px] gap-4">
              <div>
                <label className="font-semibold text-sm text-slate-700">
                  Avatar URL
                </label>
                <input
                  type="url"
                  placeholder="https://example.com/avatar.png"
                  className={`mt-1 w-full px-3 py-2 border rounded-md outline-none text-slate-700 ${
                    errors.avatarUrl ? "border-red-500" : "border-slate-300"
                  }`}
                  {...register("avatarUrl", {
                    validate: (value) =>
                      !value || isValidHttpUrl(value) || "Enter a valid image URL",
                  })}
                />
                {errors.avatarUrl?.message && (
                  <p className="text-sm text-red-600 mt-1">
                    {errors.avatarUrl.message}
                  </p>
                )}
              </div>

              <div className="border border-slate-200 rounded-lg p-3 flex items-center justify-center bg-slate-50">
                {showAvatarImage ? (
                  <img
                    src={avatarPreview}
                    alt="Avatar preview"
                    className="w-16 h-16 rounded-full object-cover border border-slate-200"
                    onError={(e) => {
                      e.currentTarget.style.display = "none";
                    }}
                  />
                ) : (
                  <div className="w-16 h-16 rounded-full bg-slate-200 text-slate-600 font-bold text-lg flex items-center justify-center">
                    {initials}
                  </div>
                )}
              </div>
            </div>

            <Button type="submit" variant="primary" size="md" loading={isSubmitting}>
              Save Profile
            </Button>
          </form>
        </section>
      </div>
    </div>
  );
};

export default ProfilePage;
