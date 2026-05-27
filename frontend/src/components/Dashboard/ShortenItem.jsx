import dayjs from "dayjs";
import PropTypes from "prop-types";
import { useEffect, useState } from "react";
import CopyToClipboard from "react-copy-to-clipboard";
import {
  FaChartBar,
  FaDownload,
  FaExternalLinkAlt,
  FaHistory,
  FaInfoCircle,
  FaRegCalendarAlt,
  FaRegEdit,
  FaTrash,
} from "react-icons/fa";
import { IoCopy } from "react-icons/io5";
import { LiaCheckSolid } from "react-icons/lia";
import { MdAnalytics, MdOutlineAdsClick, MdTimelapse } from "react-icons/md";

import Switch from "@mui/material/Switch";
import Tooltip from "@mui/material/Tooltip";

import { useNavigate } from "react-router-dom";

import toast from "react-hot-toast";

import {
  deleteShortUrl,
  disableShortUrl,
  editShortUrl,
  enableShortUrl,
  useFetchLinkPreview,
} from "../../hooks/useQuery";

import StatusBadge from "../Common/StatusBadge";
import ConfirmDialog from "../Common/ConfirmDialog";
import Button from "../Common/Button";
import Graph from "./Graph";

const TERMINAL_STATUSES = ["EXPIRED", "CLICK_LIMIT_REACHED"];

const ActionButton = ({ onClick, color, children, disabled }) => (
  <button
    type="button"
    onClick={onClick}
    disabled={disabled}
    className={`flex items-center gap-1 rounded-md px-4 py-2 font-semibold text-white shadow-md shadow-slate-500
      ${
        disabled
          ? "cursor-not-allowed opacity-40"
          : "cursor-pointer transition-opacity duration-150 hover:opacity-90"
      }
      ${color}
    `}
  >
    {children}
  </button>
);

const isValidUrl = (value) => {
  try {
    const url = new URL(value);

    return url.protocol === "http:" || url.protocol === "https:";
  } catch {
    return false;
  }
};

const ShortenItem = ({
  originalUrl,
  shortUrl,
  clickCount,
  createdDate,
  status,
  title,
  expiresAt,
  maxClicks,
  refetch,
}) => {
  const navigate = useNavigate();

  const [isCopied, setIsCopied] = useState(false);

  const [previewOpen, setPreviewOpen] = useState(false);

  const [deleteOpen, setDeleteOpen] = useState(false);

  const [deleteLoading, setDeleteLoading] = useState(false);

  const [toggleOpen, setToggleOpen] = useState(false);

  const [toggleLoading, setToggleLoading] = useState(false);

  const [currentStatus, setCurrentStatus] = useState(status);

  const [isEditing, setIsEditing] = useState(false);

  const [editableOriginalUrl, setEditableOriginalUrl] = useState(originalUrl);

  const [currentOriginalUrl, setCurrentOriginalUrl] = useState(originalUrl);

  const [editError, setEditError] = useState("");

  const [editLoading, setEditLoading] = useState(false);

  const subDomain = import.meta.env.VITE_REACT_SUBDOMAIN.replace(
    /^https?:\/\//,
    "",
  );

  const fullShortUrl = `${import.meta.env.VITE_REACT_SUBDOMAIN}/${shortUrl}`;

  const isTerminal = TERMINAL_STATUSES.includes(currentStatus);

  const isActive = currentStatus === "ACTIVE";

  useEffect(() => {
    setCurrentStatus(status);
  }, [status]);

  useEffect(() => {
    if (!isEditing) {
      setCurrentOriginalUrl(originalUrl);

      setEditableOriginalUrl(originalUrl);
    }
  }, [originalUrl, isEditing]);

  useEffect(() => {
    if (!isCopied) return;

    const timer = setTimeout(() => setIsCopied(false), 1500);

    return () => clearTimeout(timer);
  }, [isCopied]);

  const { isLoading: previewLoader, data: previewData } = useFetchLinkPreview({
    shortUrl,

    onError: () => toast.error("Could not load link preview."),

    enabled: previewOpen,
  });

  const handleEditStart = () => {
    setEditError("");

    setEditableOriginalUrl(currentOriginalUrl);

    setIsEditing(true);
  };

  const handleEditCancel = () => {
    setEditError("");

    setEditableOriginalUrl(currentOriginalUrl);

    setIsEditing(false);
  };

  const handleEditSave = async () => {
    const trimmedUrl = editableOriginalUrl.trim();

    if (!trimmedUrl) {
      setEditError("URL is required.");

      return;
    }

    if (!isValidUrl(trimmedUrl)) {
      setEditError("Please enter a valid URL (http/https).");

      return;
    }

    setEditLoading(true);

    try {
      await editShortUrl(shortUrl, {
        originalUrl: trimmedUrl,
      });

      setCurrentOriginalUrl(trimmedUrl);

      setIsEditing(false);

      setEditError("");

      toast.success("Link updated");

      await refetch();
    } catch (error) {
      const statusCode = error?.response?.status;

      if (statusCode === 400) {
        toast.error("Destination URL is invalid.");
      } else if (statusCode === 404) {
        toast.error("This link was not found.");
      } else if (statusCode === 403) {
        toast.error("You are not allowed to edit this link.");
      } else {
        toast.error("Could not update this link right now.");
      }
    } finally {
      setEditLoading(false);
    }
  };

  const handleDeleteConfirm = async () => {
    setDeleteLoading(true);

    try {
      await deleteShortUrl(shortUrl);

      toast.success("Link deleted successfully.");

      setDeleteOpen(false);

      await refetch();
    } catch (error) {
      const statusCode = error?.response?.status;

      if (statusCode === 404) {
        toast.error("Link not found.");
      } else if (statusCode === 403) {
        toast.error("You are not allowed to delete this link.");
      } else {
        toast.error("Failed to delete link. Please try again.");
      }
    } finally {
      setDeleteLoading(false);
    }
  };

  const handleSwitchChange = () => {
    if (isTerminal) return;

    if (isActive) {
      setToggleOpen(true);
    } else {
      handleToggleConfirm();
    }
  };

  const handleToggleConfirm = async () => {
    setToggleLoading(true);

    try {
      const updated = isActive
        ? await disableShortUrl(shortUrl)
        : await enableShortUrl(shortUrl);

      setCurrentStatus(updated.status);

      toast.success(
        updated.status === "ACTIVE"
          ? "Link enabled successfully."
          : "Link disabled successfully.",
      );

      setToggleOpen(false);
    } catch {
      toast.error("Failed to update link status.");
    } finally {
      setToggleLoading(false);
    }
  };

  return (
    <>
      <div
        className={`rounded-md border border-dotted border-slate-500 bg-slate-100 px-6 shadow-lg transition-all duration-150
          ${currentStatus === "DISABLED" ? "opacity-60" : ""}
        `}
      >
        <div className="flex w-full flex-col justify-between gap-5 py-5 sm:flex-row sm:gap-0">
          <div className="max-w-full flex-1 overflow-hidden sm:space-y-1">
            {title && (
              <p className="mb-0.5 text-xs font-semibold uppercase tracking-wide text-slate-500">
                {title}
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

              <StatusBadge status={currentStatus} />
            </div>

            {isEditing ? (
              <div className="mt-2">
                <input
                  type="url"
                  value={editableOriginalUrl}
                  onChange={(e) => setEditableOriginalUrl(e.target.value)}
                  className={`w-full rounded-md border px-3 py-2 text-slate-700 outline-none
                    ${editError ? "border-red-500" : "border-slate-300"}
                  `}
                  placeholder="https://example.com"
                  disabled={editLoading}
                />

                {editError && (
                  <p className="mt-1 text-xs font-medium text-red-600">
                    {editError}
                  </p>
                )}

                <div className="mt-2 flex gap-2">
                  <Button
                    variant="primary"
                    size="sm"
                    loading={editLoading}
                    onClick={handleEditSave}
                  >
                    Save
                  </Button>

                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={handleEditCancel}
                    disabled={editLoading}
                  >
                    Cancel
                  </Button>
                </div>
              </div>
            ) : (
              <div className="mt-1 flex items-start justify-between gap-2">
                <p className="break-all text-17 font-normal text-slate-700">
                  {currentOriginalUrl}
                </p>

                <Tooltip title="Edit destination">
                  <button
                    type="button"
                    onClick={handleEditStart}
                    className="rounded-md p-2 text-slate-400 transition-all hover:bg-blue-50 hover:text-blue-600"
                  >
                    <FaRegEdit className="text-base" />
                  </button>
                </Tooltip>
              </div>
            )}

            <div className="flex flex-wrap items-center gap-6 pt-4">
              <div className="flex items-center gap-1 font-semibold text-green-800">
                <MdOutlineAdsClick className="me-1 text-22" />

                <span className="text-base">{clickCount}</span>

                <span className="text-17">
                  {clickCount <= 1 ? "Click" : "Clicks"}
                </span>
              </div>

              <div className="flex items-center gap-2 font-semibold text-slate-700">
                <FaRegCalendarAlt />

                <span className="text-17">
                  {dayjs(createdDate).format("MMM DD, YYYY")}
                </span>
              </div>

              {expiresAt && (
                <div className="flex items-center gap-2 font-semibold text-amber-600">
                  <MdTimelapse className="text-lg" />

                  <span className="text-xs">
                    Expires {dayjs(expiresAt).format("MMM DD, YYYY")}
                  </span>
                </div>
              )}

              {maxClicks && (
                <div className="flex items-center gap-1 text-xs font-semibold text-slate-500">
                  <span>Limit: {maxClicks.toLocaleString()} clicks</span>
                </div>
              )}
            </div>
          </div>

          <div className="flex flex-1 flex-wrap items-center gap-2 sm:justify-end">
            <Tooltip title="Toggle link">
              <span>
                <Switch
                  checked={isActive}
                  onChange={handleSwitchChange}
                  disabled={isTerminal || toggleLoading}
                  size="small"
                  color="success"
                />
              </span>
            </Tooltip>

            <CopyToClipboard
              onCopy={() => setIsCopied(true)}
              text={fullShortUrl}
            >
              <span>
                <ActionButton color="bg-btnColor">
                  <span>{isCopied ? "Copied" : "Copy"}</span>

                  {isCopied ? <LiaCheckSolid /> : <IoCopy />}
                </ActionButton>
              </span>
            </CopyToClipboard>

            <ActionButton
              color="bg-rose-700"
              onClick={() => navigate(`/analytics/${shortUrl}`)}
            >
              <span>Analytics</span>

              <MdAnalytics />
            </ActionButton>

            <ActionButton
              color="bg-slate-700"
              onClick={() => setPreviewOpen((prev) => !prev)}
            >
              <span>{previewOpen ? "Hide Preview" : "Preview"}</span>

              <FaChartBar />
            </ActionButton>

            <Tooltip title="View history">
              <button
                type="button"
                onClick={() => navigate(`/history/${shortUrl}`)}
                className="rounded-md p-2 text-slate-400 transition-all hover:bg-indigo-50 hover:text-indigo-600"
              >
                <FaHistory className="text-base" />
              </button>
            </Tooltip>

            <Tooltip title="View details">
              <button
                type="button"
                onClick={() => navigate(`/link/${shortUrl}`)}
                className="rounded-md p-2 text-slate-400 transition-all hover:bg-blue-50 hover:text-blue-600"
              >
                <FaInfoCircle className="text-base" />
              </button>
            </Tooltip>

            <Tooltip title="Delete link">
              <button
                type="button"
                onClick={() => setDeleteOpen(true)}
                className="rounded-md p-2 text-slate-400 transition-all hover:bg-red-50 hover:text-red-500"
              >
                <FaTrash className="text-base" />
              </button>
            </Tooltip>
          </div>
        </div>

        {previewOpen && (
          <div className="mt-6 grid gap-4 xl:grid-cols-[0.85fr_1fr_350px]">
            {/* Total Clicks */}
            <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-slate-500">
                    Total Clicks
                  </p>

                  <h2 className="mt-2 text-5xl font-bold text-slate-900">
                    {clickCount ?? 0}
                  </h2>
                </div>

                <div className="rounded-xl bg-violet-100 p-3 text-violet-700">
                  <FaChartBar className="text-xl" />
                </div>
              </div>

              <div className="mt-8 h-72">
                <Graph
                  graphData={[
                    {
                      clickDate: "Clicks",
                      count: clickCount || 0,
                    },
                  ]}
                />
              </div>

              <div className="mt-6 rounded-xl bg-slate-50 p-4">
                <p className="text-sm text-slate-600">
                  This is the total number of clicks received by this short
                  link.
                </p>
              </div>
            </div>

            {/* Preview */}
            <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <h3 className="text-xl font-bold text-slate-900">Preview</h3>

              {previewLoader ? (
                <div className="mt-6 flex h-72 items-center justify-center">
                  <div className="h-8 w-8 animate-spin rounded-full border-4 border-violet-500 border-t-transparent" />
                </div>
              ) : (
                <>
                  <div className="mt-5 overflow-hidden rounded-2xl border border-slate-200 bg-slate-100">
                    <img
                      src={
                        previewData?.imageUrl ||
                        "https://placehold.co/600x300?text=Preview"
                      }
                      alt={previewData?.title}
                      className="h-56 w-full object-cover"
                    />
                  </div>

                  <div className="mt-5">
                    <h4 className="line-clamp-2 text-2xl font-bold text-slate-900">
                      {previewData?.title || "No title available"}
                    </h4>

                    <p className="mt-3 line-clamp-4 text-sm leading-7 text-slate-600">
                      {previewData?.description ||
                        "No description available for this link."}
                    </p>

                    <a
                      href={previewData?.originalUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="mt-5 inline-flex items-center gap-2 break-all text-sm font-semibold text-violet-700 hover:underline"
                    >
                      {previewData?.originalUrl}

                      <FaExternalLinkAlt className="text-xs" />
                    </a>
                  </div>
                </>
              )}
            </div>

            {/* QR */}
            <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <h3 className="text-xl font-bold text-slate-900">QR Code</h3>

              <p className="mt-1 text-sm text-slate-500">
                Scan to open this link
              </p>

              <div className="mt-6 overflow-hidden rounded-2xl border border-slate-200 bg-white p-4">
                <img
                  src={`${import.meta.env.VITE_BACKEND_URL}/api/urls/${shortUrl}/qr`}
                  alt="QR Code"
                  className="h-full w-full object-contain"
                />
              </div>

              <a
                href={`${import.meta.env.VITE_BACKEND_URL}/api/urls/${shortUrl}/qr`}
                download
                className="mt-5 flex items-center justify-center gap-2 rounded-xl border border-violet-300 px-4 py-3 text-sm font-semibold text-violet-700 transition hover:bg-violet-50"
              >
                <FaDownload />
                Download QR
              </a>
            </div>
          </div>
        )}
      </div>

      <ConfirmDialog
        open={deleteOpen}
        onClose={() => setDeleteOpen(false)}
        onConfirm={handleDeleteConfirm}
        title="Delete this link?"
        message="This link will stop redirecting immediately."
        confirmLabel="Delete"
        loading={deleteLoading}
        danger
      />

      <ConfirmDialog
        open={toggleOpen}
        onClose={() => setToggleOpen(false)}
        onConfirm={handleToggleConfirm}
        title="Disable this link?"
        message="The link will stop redirecting until re-enabled."
        confirmLabel="Disable"
        loading={toggleLoading}
      />
    </>
  );
};

ActionButton.propTypes = {
  onClick: PropTypes.func,
  color: PropTypes.string.isRequired,
  children: PropTypes.node.isRequired,
  disabled: PropTypes.bool,
};

ShortenItem.propTypes = {
  originalUrl: PropTypes.string.isRequired,

  shortUrl: PropTypes.string.isRequired,

  clickCount: PropTypes.number.isRequired,

  createdDate: PropTypes.string.isRequired,

  status: PropTypes.string.isRequired,

  title: PropTypes.string,

  expiresAt: PropTypes.string,

  maxClicks: PropTypes.number,

  refetch: PropTypes.func.isRequired,
};

ShortenItem.defaultProps = {
  title: "",
  expiresAt: null,
  maxClicks: null,
};

export default ShortenItem;
