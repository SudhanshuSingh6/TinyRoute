import PropTypes from "prop-types";
import CopyToClipboard from "react-copy-to-clipboard";
import { FaChartBar, FaHistory, FaInfoCircle, FaTrash } from "react-icons/fa";
import { IoCopy } from "react-icons/io5";
import { LiaCheckSolid } from "react-icons/lia";
import { MdAnalytics } from "react-icons/md";
import Switch from "@mui/material/Switch";
import Tooltip from "@mui/material/Tooltip";
import { useNavigate } from "react-router-dom";
import ActionButton from "../Common/ActionButton";

const ShortenItemActions = ({
  shortUrl,
  fullShortUrl,
  isCopied,
  onCopy,
  previewOpen,
  onTogglePreview,
  isActive,
  isTerminal,
  toggleLoading,
  onSwitchChange,
  onDeleteTrigger,
}) => {
  const navigate = useNavigate();
  return (
    <div className="flex flex-1 flex-wrap items-center gap-2 sm:justify-end">
      <Tooltip title="Toggle link">
        <span>
          <Switch
            checked={isActive}
            onChange={onSwitchChange}
            disabled={isTerminal || toggleLoading}
            size="small"
            color="success"
          />
        </span>
      </Tooltip>

      <CopyToClipboard onCopy={onCopy} text={fullShortUrl}>
        <span>
          <ActionButton color="bg-green-600">
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

      <ActionButton color="bg-slate-700" onClick={onTogglePreview}>
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
          onClick={onDeleteTrigger}
          className="rounded-md p-2 text-slate-400 transition-all hover:bg-red-50 hover:text-red-500"
        >
          <FaTrash className="text-base" />
        </button>
      </Tooltip>
    </div>
  );
};

ShortenItemActions.propTypes = {
  shortUrl: PropTypes.string.isRequired,
  fullShortUrl: PropTypes.string.isRequired,
  isCopied: PropTypes.bool.isRequired,
  onCopy: PropTypes.func.isRequired,
  previewOpen: PropTypes.bool.isRequired,
  onTogglePreview: PropTypes.func.isRequired,
  isActive: PropTypes.bool.isRequired,
  isTerminal: PropTypes.bool.isRequired,
  toggleLoading: PropTypes.bool.isRequired,
  onSwitchChange: PropTypes.func.isRequired,
  onDeleteTrigger: PropTypes.func.isRequired,
};

export default ShortenItemActions;
