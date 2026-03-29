import dayjs from "dayjs";
import PropTypes from "prop-types";
import { useEffect, useMemo, useState } from "react";
import CopyToClipboard from "react-copy-to-clipboard";
import {
  FaChartBar,
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
  editShortUrl,
  toggleShortUrl,
  useFetchAnalytics,
} from "../../hooks/useQuery";
import { useStoreContext } from "../../contextApi/ContextApi";
import StatusBadge from "../common/StatusBadge";
import ConfirmDialog from "../common/ConfirmDialog";
import Graph from "./Graph";
import DateRangePicker, { daysAgo, today } from "../common/DateRangePicker";
import Button from "../common/Button";
import Loader from "../common/Loader";

const TERMINAL_STATUSES = ["EXPIRED", "CLICK_LIMIT_REACHED"];

const ActionButton = ({ onClick, color, children, disabled }) => (
  <button
    type="button"
    onClick={onClick}
    disabled={disabled}
    className={`flex gap-1 items-center ${color} py-2 font-semibold shadow-md
      shadow-slate-500 px-4 rounded-md text-white
      ${
        disabled
          ? "opacity-40 cursor-not-allowed"
          : "cursor-pointer hover:opacity-90 transition-opacity duration-150"
      }`}
  >
    {children}
  </button>
);

const NoDataOverlay = () => (
  <div className="h-72 flex flex-col justify-center items-center">
    <h1 className="text-slate-800 font-montserrat sm:text-2xl text-17 font-bold mb-1">
      No Data For This Time Period
    </h1>
    <p className="text-center sm:text-base text-xs text-slate-500">
      Share your short link to start tracking clicks.
    </p>
  </div>
);

const isValidUrl = (value) => {
  try {
    const url = new URL(value);
    return url.protocol === "http:" || url.protocol === "https:";
  } catch {
    return false;
  }
};

const toGraphData = (analytics) => {
  if (!analytics) return [];

  const source =
    analytics.clicksByDate ||
    analytics.clicksByDay ||
    analytics.clicksByDateRange ||
    analytics;

  if (Array.isArray(source)) {
    return source.map((item, index) => ({
      clickDate: item.clickDate || item.date || String(index + 1),
      count: Number(item.count ?? item.clicks ?? item.value ?? 0),
    }));
  }

  if (typeof source === "object") {
    return Object.entries(source)
      .sort(([a], [b]) => new Date(a) - new Date(b))
      .map(([clickDate, count]) => ({
        clickDate,
        count: Number(count) || 0,
      }));
  }

  return [];
};

const ShortenItem = ({
  id,
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
  const { token } = useStoreContext();
  const navigate = useNavigate();

  const [isCopied, setIsCopied] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);

  const [startDate, setStartDate] = useState(daysAgo(30));
  const [endDate, setEndDate] = useState(today());

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

  const subDomain = import.meta.env.VITE_REACT_SUBDOMAIN.replace(/^https?:\/\//, "");
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

  const startDateTime = `${startDate}T00:00:00`;
  const endDateTime = `${endDate}T23:59:59`;

  const {
    isLoading: analyticsLoader,
    data: analyticsData,
  } = useFetchAnalytics(
    token,
    shortUrl,
    startDateTime,
    endDateTime,
    () => toast.error("Could not load analytics preview for this link."),
    previewOpen
  );

  const analyticsGraphData = useMemo(
    () => toGraphData(analyticsData),
    [analyticsData]
  );

  const onQuickRangeChange = (start, end) => {
    setStartDate(start);
    setEndDate(end);
  };

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
      await editShortUrl(token, id, { originalUrl: trimmedUrl });
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
      await deleteShortUrl(token, id);
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
      const updated = await toggleShortUrl(token, id);
      setCurrentStatus(updated.status);
      toast.success(
        updated.status === "ACTIVE"
          ? "Link enabled — it will redirect again."
          : "Link disabled — it will return 410 until re-enabled."
      );
      setToggleOpen(false);
    } catch (error) {
      if (error?.message?.includes("Network Error")) {
        toast.error(
          "Could not toggle link. PATCH might be blocked by backend CORS config."
        );
      } else {
        toast.error("Failed to update link status. Please try again.");
      }
    } finally {
      setToggleLoading(false);
    }
  };

  return (
    <>
      <div
        className={`bg-slate-100 shadow-lg border border-dotted border-slate-500
          px-6 sm:py-1 py-3 rounded-md transition-all duration-150
          ${currentStatus === "DISABLED" ? "opacity-60" : ""}
        `}
      >
        <div className="flex sm:flex-row flex-col sm:justify-between w-full sm:gap-0 gap-5 py-5">
          <div className="flex-1 sm:space-y-1 max-w-full overflow-hidden">
            {title && (
              <p className="text-slate-500 text-xs font-semibold uppercase tracking-wide mb-0.5">
                {title}
              </p>
            )}

            <div className="flex items-center gap-2 flex-wrap pb-1 sm:pb-0">
              <a
                href={fullShortUrl}
                target="_blank"
                rel="noreferrer"
                className="text-17 font-montserrat font-semibold text-linkColor break-all"
              >
                {subDomain}/{shortUrl}
              </a>
              <FaExternalLinkAlt className="text-linkColor text-sm shrink-0" />
              <StatusBadge status={currentStatus} />
            </div>

            {isEditing ? (
              <div className="mt-2">
                <input
                  type="url"
                  value={editableOriginalUrl}
                  onChange={(e) => setEditableOriginalUrl(e.target.value)}
                  className={`w-full px-3 py-2 border rounded-md outline-none text-slate-700 ${
                    editError ? "border-red-500" : "border-slate-300"
                  }`}
                  placeholder="https://example.com/new-destination"
                  disabled={editLoading}
                />
                {editError && (
                  <p className="text-xs text-red-600 mt-1 font-medium">{editError}</p>
                )}
                <div className="flex gap-2 mt-2">
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
              <div className="flex items-start justify-between gap-2 mt-1">
                <p className="text-slate-700 font-normal text-17 break-all">
                  {currentOriginalUrl}
                </p>
                <Tooltip title="Edit destination">
                  <button
                    type="button"
                    onClick={handleEditStart}
                    className="p-2 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-md transition-all"
                    aria-label="Edit destination URL"
                  >
                    <FaRegEdit className="text-base" />
                  </button>
                </Tooltip>
              </div>
            )}

            <div className="flex flex-wrap items-center gap-6 pt-4">
              <div className="flex gap-1 items-center font-semibold text-green-800">
                <MdOutlineAdsClick className="text-22 me-1" />
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

          <div className="flex flex-1 sm:justify-end items-center gap-2 flex-wrap">
            <Tooltip
              title={
                isTerminal
                  ? `Cannot toggle — link is ${currentStatus
                      .toLowerCase()
                      .replace("_", " ")}`
                  : isActive
                  ? "Disable link"
                  : "Enable link"
              }
            >
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

            <CopyToClipboard onCopy={() => setIsCopied(true)} text={fullShortUrl}>
              <span>
                <ActionButton color="bg-btnColor" disabled={false}>
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
              <span>{previewOpen ? "Hide" : "Preview"}</span>
              <FaChartBar />
            </ActionButton>

            <Tooltip title="View history">
              <button
                type="button"
                onClick={() => navigate(`/history/${id}`)}
                className="p-2 text-slate-400 hover:text-indigo-600 hover:bg-indigo-50 rounded-md transition-all"
                aria-label="View link history"
              >
                <FaHistory className="text-base" />
              </button>
            </Tooltip>

            <Tooltip title="View details">
              <button
                type="button"
                onClick={() => navigate(`/link/${shortUrl}`)}
                className="p-2 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-md transition-all"
                aria-label="View link details"
              >
                <FaInfoCircle className="text-base" />
              </button>
            </Tooltip>

            <Tooltip title="Delete link">
              <button
                type="button"
                onClick={() => setDeleteOpen(true)}
                className="p-2 text-slate-400 hover:text-red-500 hover:bg-red-50 rounded-md transition-all duration-150"
                aria-label="Delete link"
              >
                <FaTrash className="text-base" />
              </button>
            </Tooltip>
          </div>
        </div>

        {previewOpen && (
          <div className="border-t-2 pt-4 pb-2">
            <div className="mb-4">
              <DateRangePicker
                startDate={startDate}
                endDate={endDate}
                onChange={onQuickRangeChange}
                type="date"
                label="Quick Analytics Range"
              />
            </div>

            {analyticsLoader ? (
              <Loader message="Loading analytics preview..." />
            ) : analyticsGraphData.length === 0 ? (
              <NoDataOverlay />
            ) : (
              <div className="h-72">
                <Graph graphData={analyticsGraphData} />
              </div>
            )}
          </div>
        )}
      </div>

      <ConfirmDialog
        open={deleteOpen}
        onClose={() => setDeleteOpen(false)}
        onConfirm={handleDeleteConfirm}
        title="Delete this link?"
        message="This link will stop redirecting immediately. Analytics history will be preserved but the link cannot be restored."
        confirmLabel="Delete"
        loading={deleteLoading}
        danger
      />

      <ConfirmDialog
        open={toggleOpen}
        onClose={() => setToggleOpen(false)}
        onConfirm={handleToggleConfirm}
        title="Disable this link?"
        message="The link will return 410 Gone to anyone who visits it until you re-enable it."
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
  id: PropTypes.oneOfType([PropTypes.string, PropTypes.number]).isRequired,
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
