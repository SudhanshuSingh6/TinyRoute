import PropTypes from "prop-types";
import { useEffect, useState } from "react";
import toast from "react-hot-toast";
import {
  deleteShortUrl,
  disableShortUrl,
  enableShortUrl,
} from "../../hooks/useQuery";
import { handleApiError } from "../../utils/errorHandler";
import { useCopyToClipboard } from "../../hooks/useCopyToClipboard";
import { useConfirmAction } from "../../hooks/useConfirmAction";
import ConfirmDialog from "../Common/ConfirmDialog";
import ShortenItemHeader from "./ShortenItemHeader";
import ShortenItemActions from "./ShortenItemActions";
import ShortenItemExpanded from "./ShortenItemExpanded";

const TERMINAL_STATUSES = ["EXPIRED", "CLICK_LIMIT_REACHED"];

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
  const { copied: isCopied, copy: handleCopy } = useCopyToClipboard();
  const deleteAction = useConfirmAction();
  const toggleAction = useConfirmAction();

  const [previewOpen, setPreviewOpen] = useState(false);
  const [currentStatus, setCurrentStatus] = useState(status);

  const subDomain = import.meta.env.VITE_REACT_SUBDOMAIN.replace(/^https?:\/\//, "");
  const fullShortUrl = `${import.meta.env.VITE_REACT_SUBDOMAIN}/${shortUrl}`;
  const isTerminal = TERMINAL_STATUSES.includes(currentStatus);
  const isActive = currentStatus === "ACTIVE";

  useEffect(() => {
    setCurrentStatus(status);
  }, [status]);

  const handleDeleteConfirm = () =>
    deleteAction.confirm(
      async () => {
        await deleteShortUrl(shortUrl);
        toast.success("Link deleted successfully.");
        await refetch();
      },
      (error) =>
        handleApiError(error, {
          URL_ACCESS_DENIED: "You are not allowed to delete this link.",
        }, "Failed to delete link. Please try again."),
    );

  const handleToggleConfirm = () =>
    toggleAction.confirm(
      async () => {
        const updated = isActive
          ? await disableShortUrl(shortUrl)
          : await enableShortUrl(shortUrl);
        setCurrentStatus(updated.status);
        toast.success(
          updated.status === "ACTIVE"
            ? "Link enabled successfully."
            : "Link disabled successfully.",
        );
      },
      () => toast.error("Failed to update link status."),
    );

  const handleSwitchChange = () => {
    if (isTerminal) return;
    if (isActive) {
      toggleAction.trigger();
    } else {
      handleToggleConfirm();
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
          <ShortenItemHeader
            shortUrl={shortUrl}
            subDomain={subDomain}
            fullShortUrl={fullShortUrl}
            title={title}
            status={currentStatus}
            clickCount={clickCount}
            createdDate={createdDate}
            expiresAt={expiresAt}
            maxClicks={maxClicks}
            originalUrl={originalUrl}
            refetch={refetch}
          />
          <ShortenItemActions
            shortUrl={shortUrl}
            fullShortUrl={fullShortUrl}
            isCopied={isCopied}
            onCopy={handleCopy}
            previewOpen={previewOpen}
            onTogglePreview={() => setPreviewOpen((prev) => !prev)}
            isActive={isActive}
            isTerminal={isTerminal}
            toggleLoading={toggleAction.loading}
            onSwitchChange={handleSwitchChange}
            onDeleteTrigger={deleteAction.trigger}
          />
        </div>
        {previewOpen && (
          <ShortenItemExpanded shortUrl={shortUrl} clickCount={clickCount} />
        )}
      </div>

      <ConfirmDialog
        open={deleteAction.open}
        onClose={deleteAction.close}
        onConfirm={handleDeleteConfirm}
        title="Delete this link?"
        message="This link will stop redirecting immediately."
        confirmLabel="Delete"
        loading={deleteAction.loading}
        danger
      />

      <ConfirmDialog
        open={toggleAction.open}
        onClose={toggleAction.close}
        onConfirm={handleToggleConfirm}
        title="Disable this link?"
        message="The link will stop redirecting until re-enabled."
        confirmLabel="Disable"
        loading={toggleAction.loading}
      />
    </>
  );
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
