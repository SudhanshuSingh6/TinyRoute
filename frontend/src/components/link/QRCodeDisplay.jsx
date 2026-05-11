import { useEffect, useMemo, useState } from "react";
import PropTypes from "prop-types";
import { FaCopy, FaDownload, FaQrcode } from "react-icons/fa";
import toast from "react-hot-toast";
import api from "../../api/api";
import { API } from "../../utils/apiRoutes";
import Button from "../common/Button";
import Loader from "../common/Loader";

const QRCodeDisplay = ({ shortUrl, onNotFound }) => {
  const [qrBlobUrl, setQrBlobUrl] = useState("");
  const [loading, setLoading] = useState(true);
  const [qrError, setQrError] = useState("");

  const fullShortUrl = useMemo(
    () => `${import.meta.env.VITE_REACT_SUBDOMAIN}/${shortUrl}`,
    [shortUrl]
  );

  useEffect(() => {
    let objectUrl = "";

    const fetchQr = async () => {
      setLoading(true);
      setQrError("");

      try {
        const response = await api.get(API.QR(shortUrl), {
          responseType: "blob",
        });

        objectUrl = URL.createObjectURL(response.data);
        setQrBlobUrl(objectUrl);
      } catch (error) {
        const status = error?.response?.status;
        if (status === 404) {
          onNotFound();
          return;
        }

        setQrError("Could not load QR code.");
      } finally {
        setLoading(false);
      }
    };

    fetchQr();

    return () => {
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [shortUrl, onNotFound]);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(fullShortUrl);
      toast.success("Short URL copied.");
    } catch {
      toast.error("Could not copy the short URL.");
    }
  };

  return (
    <section className="bg-white border border-slate-200 rounded-xl p-6 shadow-card">
      <div className="flex flex-col lg:flex-row lg:items-center gap-6">
        <div className="w-full lg:w-72 flex justify-center">
          {loading ? (
            <Loader message="Loading QR..." />
          ) : qrBlobUrl ? (
            <img
              src={qrBlobUrl}
              alt={`QR code for ${shortUrl}`}
              className="w-64 h-64 object-contain border border-slate-200 rounded-lg"
            />
          ) : (
            <div className="w-64 h-64 border border-dashed border-slate-300 rounded-lg flex flex-col items-center justify-center text-slate-500">
              <FaQrcode className="text-3xl mb-2" />
              <p className="text-sm">{qrError || "QR code unavailable"}</p>
            </div>
          )}
        </div>

        <div className="flex-1">
          <p className="text-xs uppercase tracking-wide text-slate-500 font-semibold mb-1">
            Short URL
          </p>
          <p className="text-lg font-bold text-slate-900 break-all">{fullShortUrl}</p>

          <div className="flex flex-wrap gap-3 mt-4">
            <Button variant="secondary" onClick={handleCopy}>
              <FaCopy />
              Copy URL
            </Button>

            {qrBlobUrl && (
              <a href={qrBlobUrl} download={`${shortUrl}-qr.png`}>
                <Button variant="primary">
                  <FaDownload />
                  Download QR Code
                </Button>
              </a>
            )}
          </div>
        </div>
      </div>
    </section>
  );
};

QRCodeDisplay.propTypes = {
  shortUrl: PropTypes.string.isRequired,
  onNotFound: PropTypes.func,
};

QRCodeDisplay.defaultProps = {
  onNotFound: () => {},
};

export default QRCodeDisplay;
