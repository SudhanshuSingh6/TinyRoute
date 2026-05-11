import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import dayjs from "dayjs";
import toast from "react-hot-toast";
import CopyToClipboard from "react-copy-to-clipboard";
import { FaExternalLinkAlt, FaChartBar, FaTrash, FaLink, FaRegEdit, FaRegCalendarAlt } from "react-icons/fa";
import { IoCopy } from "react-icons/io5";
import { LiaCheckSolid } from "react-icons/lia";
import { MdOutlineAdsClick, MdTimelapse } from "react-icons/md";

import Loader from "../components/common/Loader";
import EmptyState from "../components/common/EmptyState";
import Button from "../components/common/Button";
import StatBlock from "../components/common/StatBlock";
import StatusBadge from "../components/common/StatusBadge";
import { useStoreContext } from "../contextApi/ContextApi";
import {
  useFetchProfile,
  useFetchMyShortUrls,
  updateProfile,
  deleteShortUrl,
} from "../hooks/useQuery";

// ─── helpers ──────────────────────────────────────────────────────────────────

const isValidHttpUrl = (v = "") => {
  try {
    const url = new URL(v);
    return url.protocol === "http:" || url.protocol === "https:";
  } catch {
    return false;
  }
};

const getInitials = (name = "") => {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (!parts.length) return "U";
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return `${parts[0][0]}${parts[1][0]}`.toUpperCase();
};

// ─── AvatarDisplay ────────────────────────────────────────────────────────────

const AvatarDisplay = ({ src, initials, size = 80 }) => {
  const [errored, setErrored] = useState(false);
  const show = src && isValidHttpUrl(src) && !errored;
  const style = {
    width: size, height: size, borderRadius: "50%",
    border: "4px solid white", boxShadow: "0 4px 14px rgba(0,0,0,0.12)",
    flexShrink: 0,
  };
  return show ? (
    <img src={src} alt={initials} onError={() => setErrored(true)} style={{ ...style, objectFit: "cover" }} />
  ) : (
    <div className="bg-custom-gradient flex items-center justify-center text-white font-bold" style={{ ...style, fontSize: size * 0.3 }}>
      {initials}
    </div>
  );
};

// ─── ProfileHero ──────────────────────────────────────────────────────────────
// Avatar + username + bio displayed ONCE.
// Toggling edit replaces the display — never both at same time.

const ProfileHero = ({ profile, initials, onSaved }) => {
  const { token } = useStoreContext();
  const [editing, setEditing] = useState(false);
  const [avatarDraft, setAvatarDraft] = useState("");
  const [bioDraft, setBioDraft] = useState("");
  const [avatarError, setAvatarError] = useState("");
  const [saving, setSaving] = useState(false);

  const openEdit = () => {
    setAvatarDraft(profile.avatarUrl ?? "");
    setBioDraft(profile.bio ?? "");
    setAvatarError("");
    setEditing(true);
  };

  const cancelEdit = () => { setEditing(false); setAvatarError(""); };

  const handleSave = async () => {
    const trimmed = avatarDraft.trim();
    if (trimmed && !isValidHttpUrl(trimmed)) {
      setAvatarError("Enter a valid http/https URL, or leave blank to remove.");
      return;
    }
    setSaving(true);
    try {
      await updateProfile(token, { bio: bioDraft.trim(), avatarUrl: trimmed });
      toast.success("Profile updated.");
      setEditing(false);
      setAvatarError("");
      onSaved();
    } catch {
      toast.error("Could not save changes.");
    } finally {
      setSaving(false);
    }
  };

  // Live preview uses draft while editing
  const previewSrc = editing ? avatarDraft.trim() : profile.avatarUrl;

  return (
    <div className="bg-white border border-slate-200 rounded-xl shadow-card overflow-hidden">
      <div className="h-24 bg-custom-gradient" />

      <div className="px-6 pb-6 flex flex-col items-center -mt-10 gap-3">
        <AvatarDisplay src={previewSrc} initials={initials} size={80} />

        {/* Username shown once — never in edit form */}
        <div className="text-center">
          <h1 className="text-xl font-bold font-montserrat text-slate-900">{profile.username}</h1>
          {profile.email && <p className="text-slate-400 text-sm mt-0.5">{profile.email}</p>}
        </div>

        {/* Bio / edit form — mutually exclusive */}
        {editing ? (
          <div className="w-full max-w-sm space-y-3">
            <div>
              <label className="text-xs font-semibold uppercase tracking-wider text-slate-400 block mb-1">Avatar URL</label>
              <input
                type="url"
                value={avatarDraft}
                onChange={(e) => { setAvatarDraft(e.target.value); setAvatarError(""); }}
                placeholder="https://example.com/avatar.png"
                className={`w-full px-3 py-2 border rounded-md outline-none text-slate-700 text-sm ${avatarError ? "border-red-400" : "border-slate-300 focus:border-btnColor"}`}
              />
              {avatarError && <p className="text-xs text-red-500 mt-1">{avatarError}</p>}
            </div>
            <div>
              <label className="text-xs font-semibold uppercase tracking-wider text-slate-400 block mb-1">Bio</label>
              <textarea
                rows={3} value={bioDraft} maxLength={300}
                onChange={(e) => setBioDraft(e.target.value)}
                placeholder="Write a short bio…"
                className="w-full px-3 py-2 border border-slate-300 focus:border-btnColor rounded-md outline-none text-slate-700 text-sm resize-none"
              />
              <p className="text-xs text-slate-400 text-right">{bioDraft.length}/300</p>
            </div>
            <div className="flex gap-2 justify-center">
              <Button variant="primary" size="sm" loading={saving} onClick={handleSave}>Save</Button>
              <Button variant="ghost" size="sm" disabled={saving} onClick={cancelEdit}>Cancel</Button>
            </div>
          </div>
        ) : (
          <div className="text-center max-w-sm">
            <p className="text-slate-600 text-sm leading-relaxed">
              {profile.bio?.trim() || <span className="text-slate-400 italic">No bio yet.</span>}
            </p>
            <button onClick={openEdit} className="mt-3 inline-flex items-center gap-1.5 text-xs font-semibold text-btnColor hover:opacity-75 transition-opacity">
              <FaRegEdit className="text-xs" /> Edit Profile
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

// ─── LinkCard ─────────────────────────────────────────────────────────────────

const LinkCard = ({ item, token, refetch }) => {
  const [copied, setCopied] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const subDomain = import.meta.env.VITE_REACT_SUBDOMAIN?.replace(/^https?:\/\//, "") ?? "";
  const fullShortUrl = `${import.meta.env.VITE_REACT_SUBDOMAIN}/${item.shortUrl}`;

  const handleDelete = async () => {
    if (!window.confirm("Delete this link? This cannot be undone.")) return;
    setDeleting(true);
    try {
      await deleteShortUrl(token, item.id);
      toast.success("Link deleted.");
      refetch?.();
    } catch {
      toast.error("Failed to delete link.");
    } finally {
      setDeleting(false);
    }
  };

  return (
    <div className="bg-white border border-slate-200 rounded-xl shadow-card px-5 py-4 hover:shadow-md transition-shadow duration-150">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          {item.title && (
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-400 mb-0.5 truncate">{item.title}</p>
          )}
          <div className="flex items-center gap-2 flex-wrap">
            <a href={fullShortUrl} target="_blank" rel="noreferrer"
              className="text-btnColor font-semibold text-sm font-montserrat hover:underline flex items-center gap-1.5 break-all">
              {subDomain}/{item.shortUrl}
              <FaExternalLinkAlt className="text-xs shrink-0" />
            </a>
            <StatusBadge status={item.status} />
          </div>
          <p className="text-slate-500 text-xs mt-1 truncate" title={item.originalUrl}>{item.originalUrl}</p>
        </div>

        <div className="flex items-center gap-1.5 shrink-0">
          <CopyToClipboard text={fullShortUrl} onCopy={() => { setCopied(true); setTimeout(() => setCopied(false), 1500); }}>
            <button className={`flex items-center gap-1 px-3 py-1.5 rounded-md text-xs font-semibold shadow-sm transition-all ${copied ? "bg-green-600 text-white" : "bg-btnColor text-white hover:opacity-90"}`}>
              {copied ? <LiaCheckSolid /> : <IoCopy />}
              {copied ? "Copied" : "Copy"}
            </button>
          </CopyToClipboard>
          <Link to={`/analytics/${item.shortUrl}`} title="Analytics"
            className="p-2 rounded-md bg-slate-100 text-slate-500 hover:bg-rose-50 hover:text-rose-600 transition-all">
            <FaChartBar className="text-sm" />
          </Link>
          <button onClick={handleDelete} disabled={deleting} title="Delete"
            className="p-2 rounded-md bg-slate-100 text-slate-500 hover:bg-red-50 hover:text-red-500 transition-all disabled:opacity-40">
            <FaTrash className="text-sm" />
          </button>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-5 mt-3 pt-3 border-t border-slate-100">
        <div className="flex items-center gap-1.5 text-green-700 font-semibold">
          <MdOutlineAdsClick className="text-base" />
          <span className="text-sm">{item.clickCount}</span>
          <span className="text-xs text-slate-400 font-normal">{item.clickCount === 1 ? "click" : "clicks"}</span>
        </div>
        <div className="flex items-center gap-1.5 text-slate-500 text-xs">
          <FaRegCalendarAlt />
          {dayjs(item.createdDate).format("MMM DD, YYYY")}
        </div>
        {item.expiresAt && (
          <div className="flex items-center gap-1.5 text-amber-600 text-xs font-medium">
            <MdTimelapse />
            Expires {dayjs(item.expiresAt).format("MMM DD, YYYY")}
          </div>
        )}
        {item.maxClicks && (
          <span className="text-xs text-slate-400">Limit: {item.maxClicks.toLocaleString()}</span>
        )}
      </div>
    </div>
  );
};

// ─── ProfilePage ──────────────────────────────────────────────────────────────

const ProfilePage = () => {
  const navigate = useNavigate();
  const { token, setToken } = useStoreContext();

  // Redirect to /register if not logged in
  useEffect(() => {
    if (!token) {
      navigate("/register", { replace: true });
    }
  }, [token, navigate]);

  const handleAuthError = (err) => {
    if ([401, 403].includes(err?.response?.status)) {
      setToken(null);
      localStorage.removeItem("JWT_TOKEN");
      navigate("/register", { replace: true });
      return;
    }
    navigate("/error");
  };

  const { isLoading: profileLoading, data: profile, refetch: refetchProfile } =
    useFetchProfile(token, handleAuthError);

  const { isLoading: linksLoading, data: links = [], refetch: refetchLinks } =
    useFetchMyShortUrls(token, () => {});

  const username = profile?.username ?? "User";
  const initials = useMemo(() => getInitials(username), [username]);
  const totalClicks = useMemo(() => links.reduce((s, l) => s + (l.clickCount ?? 0), 0), [links]);

  // Don't render anything until token check resolves
  if (!token) return null;

  if (profileLoading) return <Loader fullPage message="Loading profile…" />;

  if (!profile) {
    return (
      <div className="min-h-page flex items-center justify-center px-4">
        <p className="text-slate-500">Profile data is not available.</p>
      </div>
    );
  }

  return (
    <div className="min-h-page bg-slate-50 lg:px-14 sm:px-8 px-4 py-8">
      <div className="max-w-4xl mx-auto space-y-6">

        {/* Profile hero — avatar / username / bio / edit */}
        <ProfileHero profile={profile} initials={initials} onSaved={refetchProfile} />

        {/* Stats row */}
        <div className="grid grid-cols-3 gap-4">
          <StatBlock label="Total Links" value={links.length} color="blue" />
          <StatBlock label="Total Clicks" value={totalClicks.toLocaleString()} color="green" />
          <StatBlock label="Bio Views" value={profile.bioPageViews ?? 0} color="purple" />
        </div>

        {/* Links section */}
        <div>
          <div className="flex items-center justify-between mb-4">
            <div>
              <h2 className="text-lg font-bold font-montserrat text-slate-900">My Links</h2>
              <p className="text-xs text-slate-400 mt-0.5">
                {links.length} {links.length === 1 ? "link" : "links"} · {totalClicks.toLocaleString()} total clicks
              </p>
            </div>
            <Link to="/dashboard">
              <Button variant="secondary" size="sm">
                <FaLink className="text-xs" /> Manage Links
              </Button>
            </Link>
          </div>

          {linksLoading ? (
            <Loader message="Loading links…" />
          ) : links.length === 0 ? (
            <EmptyState
              icon={<FaLink />}
              title="No short links yet"
              subtitle="Create your first short URL from the dashboard."
              actionLabel="Go to Dashboard"
              onAction={() => navigate("/dashboard")}
            />
          ) : (
            <div className="space-y-3">
              {links.map((item) => (
                <LinkCard key={item.id} item={item} token={token} refetch={refetchLinks} />
              ))}
            </div>
          )}
        </div>

        {/* Public bio page shortcut */}
        <div className="bg-white border border-slate-200 rounded-xl shadow-card px-5 py-4 flex items-center justify-between gap-4">
          <div>
            <p className="text-sm font-semibold text-slate-700">Your public bio page</p>
            <p className="text-xs text-slate-400 mt-0.5">Anyone can view this — it shows your bio and links.</p>
          </div>
          <Link to={`/bio/${username}`} target="_blank" rel="noreferrer">
            <Button variant="secondary" size="sm">
              <FaExternalLinkAlt className="text-xs" /> View
            </Button>
          </Link>
        </div>

      </div>
    </div>
  );
};

export default ProfilePage;