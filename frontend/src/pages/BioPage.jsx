import { useEffect, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import CopyToClipboard from "react-copy-to-clipboard";
import { FaExternalLinkAlt, FaQrcode, FaLink } from "react-icons/fa";
import { IoCopy } from "react-icons/io5";
import { LiaCheckSolid } from "react-icons/lia";

import Loader from "../components/common/Loader";
import EmptyState from "../components/common/EmptyState";
import Logo from "../components/common/Logo";
import { useFetchBioPage } from "../hooks/useQuery";
import api from "../api/api";
import { API } from "../utils/apiRoutes";

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
// Shared: same implementation as ProfilePage — image or gradient initials circle.

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
    <div
      className="bg-custom-gradient flex items-center justify-center text-white font-bold"
      style={{ ...style, fontSize: size * 0.3 }}
    >
      {initials}
    </div>
  );
};

// ─── QRDropdown ───────────────────────────────────────────────────────────────
// Fetches and shows the QR code for a short URL in a floating dropdown.
// Closes on outside click.

const QRDropdown = ({ shortUrl }) => {
  const [open, setOpen] = useState(false);
  const [qrSrc, setQrSrc] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const ref = useRef(null);

  // Close on outside click
  useEffect(() => {
    if (!open) return;
    const handler = (e) => {
      if (ref.current && !ref.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [open]);

  const toggle = async () => {
    if (open) { setOpen(false); return; }
    setOpen(true);
    if (qrSrc) return; // already fetched
    setLoading(true);
    setError(false);
    try {
      const res = await api.get(API.QR(shortUrl), { responseType: "blob" });
      setQrSrc(URL.createObjectURL(res.data));
    } catch {
      setError(true);
    } finally {
      setLoading(false);
    }
  };

  // Cleanup blob URL on unmount
  useEffect(() => {
    return () => { if (qrSrc) URL.revokeObjectURL(qrSrc); };
  }, [qrSrc]);

  return (
    <div ref={ref} className="relative">
      <button
        onClick={toggle}
        title="Show QR code"
        className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-semibold border transition-all ${
          open
            ? "bg-btnColor text-white border-btnColor"
            : "bg-white text-slate-600 border-slate-300 hover:border-btnColor hover:text-btnColor"
        }`}
      >
        <FaQrcode className="text-sm" />
        QR
      </button>

      {open && (
        <div className="absolute right-0 top-full mt-2 z-50 bg-white border border-slate-200 rounded-xl shadow-custom p-4 flex flex-col items-center gap-3"
          style={{ width: 200 }}>
          <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">QR Code</p>

          {loading && (
            <div className="w-36 h-36 flex items-center justify-center">
              <div className="w-6 h-6 border-2 border-btnColor border-t-transparent rounded-full animate-spin" />
            </div>
          )}

          {error && !loading && (
            <div className="w-36 h-36 flex flex-col items-center justify-center text-slate-400 gap-2">
              <FaQrcode className="text-3xl" />
              <p className="text-xs text-center">Could not load QR code</p>
            </div>
          )}

          {qrSrc && !loading && !error && (
            <>
              <img src={qrSrc} alt={`QR code for ${shortUrl}`}
                className="w-36 h-36 object-contain rounded-lg border border-slate-100" />
              <a
                href={qrSrc}
                download={`${shortUrl}-qr.png`}
                className="text-xs font-semibold text-btnColor hover:underline"
              >
                Download PNG
              </a>
            </>
          )}
        </div>
      )}
    </div>
  );
};

// ─── PublicLinkCard ───────────────────────────────────────────────────────────
// Shows title, short URL (copy + QR), and full destination URL as a clickable link.
// No click counts, no limits, no analytics — public-facing only.

const PublicLinkCard = ({ item }) => {
  const [copied, setCopied] = useState(false);
  const fullShortUrl = `${import.meta.env.VITE_REACT_SUBDOMAIN}/${item.shortUrl}`;
  const subDomain = import.meta.env.VITE_REACT_SUBDOMAIN?.replace(/^https?:\/\//, "") ?? "";

  return (
    <div className="bg-white border border-slate-200 rounded-xl shadow-card px-5 py-4 hover:shadow-md transition-shadow duration-150">

      {/* Title */}
      {item.title?.trim() && (
        <p className="text-xs font-semibold uppercase tracking-wide text-slate-400 mb-2">
          {item.title}
        </p>
      )}

      {/* Short URL row — copy + QR */}
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <span className="text-btnColor font-semibold text-sm font-montserrat break-all">
          {subDomain}/{item.shortUrl}
        </span>

        <div className="flex items-center gap-2 shrink-0">
          <CopyToClipboard
            text={fullShortUrl}
            onCopy={() => { setCopied(true); setTimeout(() => setCopied(false), 1500); }}
          >
            <button
              title="Copy short URL"
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-semibold border transition-all ${
                copied
                  ? "bg-green-600 text-white border-green-600"
                  : "bg-white text-slate-600 border-slate-300 hover:border-btnColor hover:text-btnColor"
              }`}
            >
              {copied ? <LiaCheckSolid /> : <IoCopy />}
              {copied ? "Copied" : "Copy"}
            </button>
          </CopyToClipboard>

          <QRDropdown shortUrl={item.shortUrl} />
        </div>
      </div>

      {/* Divider */}
      <div className="border-t border-slate-100 mt-3 pt-3">
        {/* Full destination URL — opens the short URL (which will redirect) */}
        <a
          href={fullShortUrl}
          target="_blank"
          rel="noreferrer"
          className="flex items-center gap-2 text-slate-500 text-xs hover:text-btnColor transition-colors group"
          title={item.originalUrl}
        >
          <FaExternalLinkAlt className="text-xs shrink-0 text-slate-400 group-hover:text-btnColor transition-colors" />
          <span className="truncate">{item.originalUrl}</span>
        </a>
      </div>
    </div>
  );
};

// ─── BioPage ──────────────────────────────────────────────────────────────────

const BioPage = () => {
  const { username } = useParams();
  const [notFound, setNotFound] = useState(false);
  const [loadError, setLoadError] = useState(false);

  const { isLoading, data } = useFetchBioPage(username, (err) => {
    if (err?.response?.status === 404) { setNotFound(true); return; }
    setLoadError(true);
  });

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50">
        <Loader message="Loading profile…" />
      </div>
    );
  }

  if (notFound) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50 px-4">
        <EmptyState
          icon={<FaLink />}
          title="Profile not found"
          subtitle={`We couldn't find a user named "${username}".`}
        />
      </div>
    );
  }

  if (loadError) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50 px-4">
        <EmptyState title="Something went wrong" subtitle="Could not load this profile right now." />
      </div>
    );
  }

  // Normalise API response — backend returns { profile, urls } or { profile, links }
  const payload = data ?? {};
  const links = Array.isArray(payload.urls)
    ? payload.urls
    : Array.isArray(payload.links)
    ? payload.links
    : [];

  const displayUsername = payload.username ?? username;
  const initials = getInitials(displayUsername);
  const avatarUrl = payload.avatarUrl ?? "";
  const bio = payload.bio;

  return (
    <div className="min-h-screen bg-slate-50 py-10 px-4">
      <div className="max-w-xl mx-auto space-y-6">

        {/* ── Profile hero — centered, read-only ── */}
        <div className="bg-white border border-slate-200 rounded-xl shadow-card overflow-hidden">
          <div className="h-24 bg-custom-gradient" />

          <div className="px-6 pb-6 flex flex-col items-center -mt-10 gap-3">
            <AvatarDisplay src={avatarUrl} initials={initials} size={80} />

            <div className="text-center">
              <h1 className="text-xl font-bold font-montserrat text-slate-900">{displayUsername}</h1>
            </div>

            {bio.trim() ? (
              <p className="text-slate-600 text-sm leading-relaxed text-center max-w-xs">{bio}</p>
            ) : (
              <p className="text-slate-400 text-sm italic text-center">No bio available.</p>
            )}
          </div>
        </div>

        {/* ── Public links ── */}
        <div>
          <h2 className="text-sm font-bold font-montserrat text-slate-500 uppercase tracking-wider mb-3">
            Links
          </h2>

          {links.length === 0 ? (
            <EmptyState
              icon={<FaLink />}
              title="No public links"
              subtitle="This user hasn't shared any links yet."
            />
          ) : (
            <div className="space-y-3">
              {links.map((item, idx) => (
                <PublicLinkCard key={item.shortUrl ?? idx} item={item} />
              ))}
            </div>
          )}
        </div>

        {/* ── Footer branding ── */}
        <div className="flex items-center justify-center gap-2 py-4 text-slate-400 text-xs">
          <Logo size="sm" variant="dark" iconOnly />
          <span>Powered by TinyRoute</span>
        </div>

      </div>
    </div>
  );
};

export default BioPage;