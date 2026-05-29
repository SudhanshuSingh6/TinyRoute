import dayjs from "dayjs";
import PropTypes from "prop-types";
import { useEffect, useState } from "react";
import {
  FaExternalLinkAlt,
  FaRegCalendarAlt,
  FaRegEdit,
} from "react-icons/fa";
import { MdEditCalendar, MdOutlineAdsClick, MdTimelapse } from "react-icons/md";
import Tooltip from "@mui/material/Tooltip";
import toast from "react-hot-toast";
import StatusBadge from "../Common/StatusBadge";
import Button from "../Common/Button";
import { editShortUrl, editShortUrlExpiry } from "../../hooks/useQuery";
import { handleApiError } from "../../utils/errorHandler";
import { isValidHttpUrl } from "../../utils/helper";

const toDateTimeLocal = (value) =>
  value ? dayjs(value).format("YYYY-MM-DDTHH:mm") : "";

const ShortenItemHeader = ({
  shortUrl,
  subDomain,
  fullShortUrl,
  title,
  status,
  clickCount,
  createdDate,
  expiresAt,
  maxClicks,
  originalUrl,
  refetch,
}) => {
  const [isEditing, setIsEditing] = useState(false);
  const [editableOriginalUrl, setEditableOriginalUrl] = useState(originalUrl);
  const [currentOriginalUrl, setCurrentOriginalUrl] = useState(originalUrl);
  const [editableTitle, setEditableTitle] = useState(title);
  const [currentTitle, setCurrentTitle] = useState(title);
  const [editError, setEditError] = useState("");
  const [editLoading, setEditLoading] = useState(false);

  const [isEditingExpiry, setIsEditingExpiry] = useState(false);
  const [editableExpiresAt, setEditableExpiresAt] = useState(toDateTimeLocal(expiresAt));
  const [currentExpiresAt, setCurrentExpiresAt] = useState(expiresAt);
  const [expiryError, setExpiryError] = useState("");
  const [expiryLoading, setExpiryLoading] = useState(false);

  useEffect(() => {
    if (!isEditing) {
      setCurrentOriginalUrl(originalUrl);
      setEditableOriginalUrl(originalUrl);
      setCurrentTitle(title);
      setEditableTitle(title);
    }
  }, [originalUrl, title, isEditing]);

  useEffect(() => {
    if (!isEditingExpiry) {
      setCurrentExpiresAt(expiresAt);
      setEditableExpiresAt(toDateTimeLocal(expiresAt));
    }
  }, [expiresAt, isEditingExpiry]);

  const handleEditStart = () => {
    setEditError("");
    setEditableOriginalUrl(currentOriginalUrl);
    setEditableTitle(currentTitle);
    setIsEditing(true);
  };

  const handleEditCancel = () => {
    setEditError("");
    setEditableOriginalUrl(currentOriginalUrl);
    setEditableTitle(currentTitle);
    setIsEditing(false);
  };

  const handleEditSave = async () => {
    const trimmedUrl = editableOriginalUrl.trim();
    const trimmedTitle = (editableTitle ?? "").trim();
    if (!trimmedUrl) { setEditError("URL is required."); return; }
    if (!isValidHttpUrl(trimmedUrl)) { setEditError("Please enter a valid URL (http/https)."); return; }
    if (trimmedTitle.length > 150) { setEditError("Title must be 150 characters or fewer."); return; }
    setEditLoading(true);
    try {
      await editShortUrl(shortUrl, { originalUrl: trimmedUrl, title: trimmedTitle });
      setCurrentOriginalUrl(trimmedUrl);
      setCurrentTitle(trimmedTitle);
      setIsEditing(false);
      setEditError("");
      toast.success("Link updated");
      await refetch();
    } catch (error) {
      handleApiError(error, {
        URL_ACCESS_DENIED: "You are not allowed to edit this link.",
      }, "Could not update this link right now.");
    } finally {
      setEditLoading(false);
    }
  };

  const handleExpiryEditStart = () => {
    setExpiryError("");
    setEditableExpiresAt(toDateTimeLocal(currentExpiresAt));
    setIsEditingExpiry(true);
  };

  const handleExpiryEditCancel = () => {
    setExpiryError("");
    setEditableExpiresAt(toDateTimeLocal(currentExpiresAt));
    setIsEditingExpiry(false);
  };

  const handleExpiryEditSave = async () => {
    if (editableExpiresAt && new Date(editableExpiresAt).getTime() <= Date.now()) {
      setExpiryError("Expiry must be in the future.");
      return;
    }
    const normalizedExpiresAt = editableExpiresAt
      ? editableExpiresAt.length === 16
        ? `${editableExpiresAt}:00`
        : editableExpiresAt
      : null;
    setExpiryLoading(true);
    try {
      await editShortUrlExpiry(shortUrl, { expiresAt: normalizedExpiresAt });
      setCurrentExpiresAt(normalizedExpiresAt);
      setIsEditingExpiry(false);
      setExpiryError("");
      toast.success(normalizedExpiresAt ? "Expiry updated" : "Expiry removed");
      await refetch();
    } catch (error) {
      handleApiError(error, {
        URL_ACCESS_DENIED: "You are not allowed to edit this link.",
      }, "Could not update the expiry right now.");
    } finally {
      setExpiryLoading(false);
    }
  };

  return (
    <div className="max-w-full flex-1 overflow-hidden sm:space-y-1">
      {currentTitle && (
        <p className="mb-0.5 text-xs font-semibold uppercase tracking-wide text-slate-500">
          {currentTitle}
        </p>
      )}

      <div className="flex flex-wrap items-center gap-2 pb-1 sm:pb-0">
        <a
          href={fullShortUrl}
          target="_blank"
          rel="noreferrer"
          className="break-all font-montserrat text-17 font-semibold text-linkColor"
        >
          {subDomain}/{shortUrl}
        </a>
        <FaExternalLinkAlt className="shrink-0 text-sm text-linkColor" />
        <StatusBadge status={status} />
      </div>

      {isEditing ? (
        <div className="mt-2">
          <label className="text-xs font-semibold text-slate-500">Title (optional)</label>
          <input
            type="text"
            value={editableTitle ?? ""}
            onChange={(e) => setEditableTitle(e.target.value)}
            maxLength={150}
            className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-slate-700 outline-none"
            placeholder="My link"
            disabled={editLoading}
          />
          <label className="mt-2 block text-xs font-semibold text-slate-500">Destination</label>
          <input
            type="url"
            value={editableOriginalUrl}
            onChange={(e) => setEditableOriginalUrl(e.target.value)}
            className={`mt-1 w-full rounded-md border px-3 py-2 text-slate-700 outline-none
              ${editError ? "border-red-500" : "border-slate-300"}
            `}
            placeholder="https://example.com"
            disabled={editLoading}
          />
          {editError && (
            <p className="mt-1 text-xs font-medium text-red-600">{editError}</p>
          )}
          <div className="mt-2 flex gap-2">
            <Button variant="primary" size="sm" loading={editLoading} onClick={handleEditSave}>
              Save
            </Button>
            <Button variant="ghost" size="sm" onClick={handleEditCancel} disabled={editLoading}>
              Cancel
            </Button>
          </div>
        </div>
      ) : (
        <div className="mt-1 flex items-start gap-1">
          <p className="break-all text-17 font-normal text-slate-700">
            {currentOriginalUrl}
          </p>
          <Tooltip title="Edit destination">
            <button
              type="button"
              onClick={handleEditStart}
              className="shrink-0 rounded-md p-2 text-slate-400 transition-all hover:bg-blue-50 hover:text-blue-600"
            >
              <FaRegEdit className="text-base" />
            </button>
          </Tooltip>
        </div>
      )}

      {isEditingExpiry && (
        <div className="mt-2">
          <label className="text-xs font-semibold text-slate-500">
            Expires at (leave empty for no expiry)
          </label>
          <input
            type="datetime-local"
            value={editableExpiresAt}
            onChange={(e) => setEditableExpiresAt(e.target.value)}
            className={`mt-1 w-full rounded-md border px-3 py-2 text-slate-700 outline-none
              ${expiryError ? "border-red-500" : "border-slate-300"}
            `}
            disabled={expiryLoading}
          />
          {expiryError && (
            <p className="mt-1 text-xs font-medium text-red-600">{expiryError}</p>
          )}
          <div className="mt-2 flex gap-2">
            <Button variant="primary" size="sm" loading={expiryLoading} onClick={handleExpiryEditSave}>
              Save
            </Button>
            <Button variant="ghost" size="sm" onClick={handleExpiryEditCancel} disabled={expiryLoading}>
              Cancel
            </Button>
          </div>
        </div>
      )}

      <div className="flex flex-wrap items-center gap-6 pt-4">
        <div className="flex items-center gap-1 font-semibold text-green-800">
          <MdOutlineAdsClick className="me-1 text-22" />
          <span className="text-base">{clickCount}</span>
          <span className="text-17">{clickCount <= 1 ? "Click" : "Clicks"}</span>
        </div>
        <div className="flex items-center gap-2 font-semibold text-slate-700">
          <FaRegCalendarAlt />
          <span className="text-17">{dayjs(createdDate).format("MMM DD, YYYY")}</span>
        </div>
        <div className="flex items-center gap-1 font-semibold text-amber-600">
          <MdTimelapse className="text-lg" />
          <span className="text-xs">
            {currentExpiresAt
              ? `Expires ${dayjs(currentExpiresAt).format("MMM DD, YYYY")}`
              : "No expiry"}
          </span>
          <Tooltip title="Edit expiry">
            <button
              type="button"
              onClick={handleExpiryEditStart}
              className="shrink-0 rounded-md p-1 text-amber-400 transition-all hover:bg-amber-50 hover:text-amber-600"
            >
              <MdEditCalendar className="text-base" />
            </button>
          </Tooltip>
        </div>
        {maxClicks && (
          <div className="flex items-center gap-1 text-xs font-semibold text-slate-500">
            <span>Limit: {maxClicks.toLocaleString()} clicks</span>
          </div>
        )}
      </div>
    </div>
  );
};

ShortenItemHeader.propTypes = {
  shortUrl: PropTypes.string.isRequired,
  subDomain: PropTypes.string.isRequired,
  fullShortUrl: PropTypes.string.isRequired,
  title: PropTypes.string,
  status: PropTypes.string.isRequired,
  clickCount: PropTypes.number.isRequired,
  createdDate: PropTypes.string.isRequired,
  expiresAt: PropTypes.string,
  maxClicks: PropTypes.number,
  originalUrl: PropTypes.string.isRequired,
  refetch: PropTypes.func.isRequired,
};

export default ShortenItemHeader;
