import PropTypes from "prop-types";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import Button from "./Button";

const ConfirmDialog = ({
  open,
  onClose,
  onConfirm,
  title,
  message,
  confirmLabel,
  loading,
  danger,
}) => {
  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="xs"
      fullWidth
      PaperProps={{
        style: { borderRadius: "12px", padding: "4px" },
      }}
    >
      <DialogTitle
        sx={{
          fontWeight: 700,
          fontSize: "1.1rem",
          fontFamily: "Montserrat, sans-serif",
          paddingBottom: "8px",
        }}
      >
        {title}
      </DialogTitle>

      <DialogContent>
        <p className="text-slate-500 text-sm leading-relaxed">{message}</p>
      </DialogContent>

      <DialogActions sx={{ padding: "12px 24px 16px", gap: "8px" }}>
        <Button
          variant="ghost"
          size="sm"
          onClick={onClose}
          disabled={loading}
          className="text-slate-600"
        >
          Cancel
        </Button>
        <Button
          variant={danger ? "danger" : "primary"}
          size="sm"
          onClick={onConfirm}
          loading={loading}
        >
          {confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

ConfirmDialog.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  onConfirm: PropTypes.func.isRequired,
  title: PropTypes.string.isRequired,
  message: PropTypes.string.isRequired,
  confirmLabel: PropTypes.string,
  loading: PropTypes.bool,
  danger: PropTypes.bool,
};

ConfirmDialog.defaultProps = {
  confirmLabel: "Confirm",
  loading: false,
  danger: false,
};

export default ConfirmDialog;
