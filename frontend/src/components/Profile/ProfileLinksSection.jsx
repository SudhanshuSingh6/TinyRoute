import PropTypes from "prop-types";
import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import dayjs from "dayjs";
import CopyToClipboard from "react-copy-to-clipboard";
import {
  FaChartBar,
  FaExternalLinkAlt,
  FaLink,
  FaRegCalendarAlt,
  FaTrash,
} from "react-icons/fa";
import { IoCopy } from "react-icons/io5";
import { LiaCheckSolid } from "react-icons/lia";
import { MdOutlineAdsClick, MdTimelapse } from "react-icons/md";
import toast from "react-hot-toast";
import Loader from "../Common/Loader";
import Button from "../Common/Button";
import StatusBadge from "../Common/StatusBadge";
import EmptyState from "../Common/EmptyState";
import { deleteShortUrl } from "../../hooks/useQuery";
import { useCopyToClipboard } from "../../hooks/useCopyToClipboard";

// ─── LinkCard ─────────────────────────────────────────────────────────────────

const LinkCard = ({ item, refetch }) => {
  const { copied, copy } = useCopyToClipboard();
  const [deleting, setDeleting] = useState(false);
  const subDomain =
    import.meta.env.VITE_REACT_SUBDOMAIN?.replace(/^https?:\/\//, "") ?? "";
  const fullShortUrl = `${import.meta.env.VITE_REACT_SUBDOMAIN}/${item.shortUrl}`;

  const handleDelete = async () => {
    if (!window.confirm("Delete this link? This cannot be undone.")) return;
    setDeleting(true);
    try {
      await deleteShortUrl(item.id);
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
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-400 mb-0.5 truncate">
              {item.title}
            </p>
          )}
          <div className="flex items-center gap-2 flex-wrap">
            <a
              href={fullShortUrl}
              target="_blank"
              rel="noreferrer"
              className="text-btnColor font-semibold text-sm font-montserrat hover:underline flex items-center gap-1.5 break-all"
            >
              {subDomain}/{item.shortUrl}
              <FaExternalLinkAlt className="text-xs shrink-0" />
            </a>
            <StatusBadge status={item.status} />
          </div>
          <p
            className="text-slate-500 text-xs mt-1 truncate"
            title={item.originalUrl}
          >
            {item.originalUrl}
          </p>
        </div>

        <div className="flex items-center gap-1.5 shrink-0">
          <CopyToClipboard text={fullShortUrl} onCopy={copy}>
            <button
              className={`flex items-center gap-1 px-3 py-1.5 rounded-md text-xs font-semibold shadow-sm transition-all duration-200 ${
                copied
                  ? "bg-green-600 text-white"
                  : "bg-white text-slate-600 border border-slate-300 hover:bg-slate-50 hover:border-slate-400"
              }`}
            >
              {copied ? <LiaCheckSolid /> : <IoCopy />}
              {copied ? "Copied" : "Copy"}
            </button>
          </CopyToClipboard>
          <Link
            to={`/analytics/${item.shortUrl}`}
            title="Analytics"
            className="p-2 rounded-md bg-slate-100 text-slate-500 hover:bg-rose-50 hover:text-rose-600 transition-all"
          >
            <FaChartBar className="text-sm" />
          </Link>
          <button
            onClick={handleDelete}
            disabled={deleting}
            title="Delete"
            className="p-2 rounded-md bg-slate-100 text-slate-500 hover:bg-red-50 hover:text-red-500 transition-all disabled:opacity-40"
          >
            <FaTrash className="text-sm" />
          </button>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-5 mt-3 pt-3 border-t border-slate-100">
        <div className="flex items-center gap-1.5 text-green-700 font-semibold">
          <MdOutlineAdsClick className="text-base" />
          <span className="text-sm">{item.clickCount}</span>
          <span className="text-xs text-slate-400 font-normal">
            {item.clickCount === 1 ? "click" : "clicks"}
          </span>
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
          <span className="text-xs text-slate-400">
            Limit: {item.maxClicks.toLocaleString()}
          </span>
        )}
      </div>
    </div>
  );
};

// ─── ProfileLinksSection ──────────────────────────────────────────────────────

const ProfileLinksSection = ({ links, isLoading, totalClicks, refetch }) => {
  const navigate = useNavigate();
  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <div>
          <h2 className="text-lg font-bold font-montserrat text-slate-900">
            My Links
          </h2>
          <p className="text-xs text-slate-400 mt-0.5">
            {links.length} {links.length === 1 ? "link" : "links"} ·{" "}
            {totalClicks.toLocaleString()} total clicks
          </p>
        </div>
        <Link to="/dashboard">
          <Button variant="secondary" size="sm">
            <FaLink className="text-xs" /> Manage Links
          </Button>
        </Link>
      </div>

      {isLoading ? (
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
            <LinkCard key={item.id} item={item} refetch={refetch} />
          ))}
        </div>
      )}
    </div>
  );
};

ProfileLinksSection.propTypes = {
  links: PropTypes.array.isRequired,
  isLoading: PropTypes.bool.isRequired,
  totalClicks: PropTypes.number.isRequired,
  refetch: PropTypes.func.isRequired,
};

export default ProfileLinksSection;
