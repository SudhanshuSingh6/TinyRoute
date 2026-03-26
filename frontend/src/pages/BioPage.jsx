import { useMemo, useState } from "react";
import { useParams } from "react-router-dom";
import { FaLink, FaUserCircle } from "react-icons/fa";
import Loader from "../components/common/Loader";
import EmptyState from "../components/common/EmptyState";
import StatusBadge from "../components/common/StatusBadge";
import StatBlock from "../components/common/StatBlock";
import { useFetchBioPage } from "../hooks/useQuery";

const ALLOWED_STATUSES = new Set([
  "ACTIVE",
  "DISABLED",
  "EXPIRED",
  "CLICK_LIMIT_REACHED",
]);

const normalizeStatus = (value) => {
  const status = String(value || "ACTIVE").toUpperCase();
  return ALLOWED_STATUSES.has(status) ? status : "DISABLED";
};

const getInitials = (value = "") => {
  const parts = value.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "U";
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return `${parts[0][0]}${parts[1][0]}`.toUpperCase();
};

const BioPage = () => {
  const { username } = useParams();
  const [notFound, setNotFound] = useState(false);
  const [loadError, setLoadError] = useState("");

  const onError = (error) => {
    if (error?.response?.status === 404) {
      setNotFound(true);
      return;
    }
    setLoadError("Could not load this bio page right now.");
  };

  const { isLoading, data } = useFetchBioPage(username, onError);

  const payload = data ?? {};
  const profile = Array.isArray(payload) ? {} : payload.profile ?? payload;

  const publicLinks = useMemo(() => {
    if (Array.isArray(payload)) return payload;
    if (Array.isArray(payload.links)) return payload.links;
    if (Array.isArray(payload.publicLinks)) return payload.publicLinks;
    if (Array.isArray(payload.urls)) return payload.urls;
    if (Array.isArray(profile.links)) return profile.links;
    return [];
  }, [payload, profile]);

  const displayUsername = profile?.username ?? username;
  const bio = profile?.bio ?? "";
  const avatarUrl = profile?.avatarUrl ?? "";
  const bioViews =
    profile?.bioPageViews ?? profile?.bioViews ?? profile?.views ?? 0;

  const isAvatarValid =
    avatarUrl &&
    (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://"));

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50 px-4">
        <Loader message="Loading bio page..." />
      </div>
    );
  }

  if (notFound) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50 px-4">
        <EmptyState
          title="This bio page doesn't exist"
          subtitle="Please check the username and try again."
        />
      </div>
    );
  }

  if (loadError) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50 px-4">
        <p className="text-slate-600">{loadError}</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50 py-8 px-4">
      <div className="max-w-2xl mx-auto space-y-4">
        <section className="bg-white border border-slate-200 rounded-xl p-6 shadow-card">
          <div className="flex flex-col sm:flex-row sm:items-center gap-4">
            {isAvatarValid ? (
              <img
                src={avatarUrl}
                alt={`${displayUsername} avatar`}
                className="w-20 h-20 rounded-full object-cover border border-slate-200"
                onError={(e) => {
                  e.currentTarget.style.display = "none";
                }}
              />
            ) : (
              <div className="w-20 h-20 rounded-full bg-custom-gradient text-white font-bold text-2xl flex items-center justify-center">
                {getInitials(displayUsername)}
              </div>
            )}

            <div className="flex-1">
              <p className="text-xs uppercase tracking-wide text-slate-500 font-semibold">
                Public Bio
              </p>
              <h1 className="text-2xl font-bold text-slate-900">{displayUsername}</h1>
              <p className="text-slate-600 mt-2">
                {bio?.trim() || "No bio available yet."}
              </p>
            </div>

            <div className="w-full sm:w-44">
              <StatBlock label="Views" value={bioViews} color="blue" />
            </div>
          </div>
        </section>

        <section className="bg-white border border-slate-200 rounded-xl p-6 shadow-card">
          <h2 className="text-lg font-bold text-slate-900 mb-4">Public Links</h2>

          {publicLinks.length === 0 ? (
            <EmptyState
              icon={<FaLink />}
              title="No public links yet"
              subtitle="This profile has not shared any public links."
            />
          ) : (
            <div className="space-y-3">
              {publicLinks.map((link, index) => {
                const shortCode = link.shortUrl ?? link.code ?? "";
                const title = link.title?.trim() || shortCode || "Untitled Link";
                const originalUrl = link.originalUrl ?? link.url ?? "";
                const status = normalizeStatus(link.status);
                const clickCount = Number(link.clickCount ?? 0);
                const href = `${import.meta.env.VITE_REACT_SUBDOMAIN}/${shortCode}`;

                return (
                  <a
                    key={`${shortCode}-${index}`}
                    href={href}
                    className="block border border-slate-200 rounded-lg p-4 hover:border-btnColor hover:shadow-sm transition-all"
                  >
                    <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
                      <div className="min-w-0">
                        <p className="font-semibold text-slate-900 truncate">{title}</p>
                        <p className="text-slate-500 text-sm truncate">
                          {originalUrl || "No original URL provided"}
                        </p>
                      </div>

                      <div className="flex items-center gap-3">
                        <StatusBadge status={status} />
                        <p className="text-sm text-slate-600 font-medium">
                          {clickCount} {clickCount === 1 ? "click" : "clicks"}
                        </p>
                      </div>
                    </div>
                  </a>
                );
              })}
            </div>
          )}
        </section>

        <div className="flex items-center justify-center text-slate-400 text-sm gap-2 py-2">
          <FaUserCircle />
          <span>Powered by TinyRoute</span>
        </div>
      </div>
    </div>
  );
};

export default BioPage;
