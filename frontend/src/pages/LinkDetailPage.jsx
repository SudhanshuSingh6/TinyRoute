import { useEffect, useMemo, useState } from "react";
import { useParams } from "react-router-dom";
import { FaCopy, FaDownload, FaExternalLinkAlt, FaQrcode } from "react-icons/fa";
import toast from "react-hot-toast";
import Button from "../components/common/Button";
import EmptyState from "../components/common/EmptyState";
import Loader from "../components/common/Loader";
import api from "../api/api";
import { API } from "../utils/apiRoutes";
import { useFetchLinkPreview } from "../hooks/useQuery";

const LinkPreviewCard = ({ preview, fallbackUrl }) => {
  const title = preview?.title ?? preview?.ogTitle ?? "";
  const description = preview?.description ?? preview?.ogDescription ?? "";
  const image = preview?.image ?? preview?.ogImage ?? preview?.imageUrl ?? "";
  const sourceUrl = preview?.url ?? preview?.originalUrl ?? fallbackUrl;

  const hasMetadata = Boolean(title || description || image);

  return (
    <section className="bg-white border border-slate-200 rounded-xl p-6 shadow-card">
      <h2 className="text-lg font-bold text-slate-900 mb-4">Link Preview</h2>

      {!hasMetadata ? (
        <div className="border border-slate-200 rounded-lg p-4 bg-slate-50">
          <p className="text-slate-800 font-semibold break-all">{sourceUrl}</p>
          <p className="text-slate-500 text-sm mt-2">
            Preview metadata is not available for this URL.
          </p>
        </div>
      ) : (
        <div className="border border-slate-200 rounded-lg overflow-hidden">
          {image && (
            <img
              src={image}
              alt={title || "Link preview"}
              className="w-full h-56 object-cover bg-slate-100"
            />
          )}

          <div className="p-4 space-y-2">
            {title && <h3 className="text-slate-900 font-bold">{title}</h3>}
            {description && <p className="text-slate-600 text-sm">{description}</p>}
            <a
              href={sourceUrl}
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-2 text-btnColor font-semibold text-sm break-all hover:underline"
            >
              {sourceUrl}
              <FaExternalLinkAlt className="text-xs" />
            </a>
          </div>
        </div>
      )}
    </section>
  );
};

const LinkDetailPage = () => {
  const { shortUrl } = useParams();

  const [qrBlobUrl, setQrBlobUrl] = useState("");
  const [qrLoading, setQrLoading] = useState(true);
  const [qrError, setQrError] = useState("");
  const [previewError, setPreviewError] = useState("");
  const [notFound, setNotFound] = useState(false);

  const fullShortUrl = useMemo(
    () => `${import.meta.env.VITE_REACT_SUBDOMAIN}/${shortUrl}`,
    [shortUrl]
  );

  const { isLoading: previewLoading, data: preview } = useFetchLinkPreview(
    shortUrl,
    (error) => {
      if (error?.response?.status === 404) {
        setNotFound(true);
      } else {
        setPreviewError("Could not load preview metadata.");
      }
    }
  );

  useEffect(() => {
    let objectUrl = "";

    const fetchQr = async () => {
      setQrLoading(true);
      setQrError("");

      try {
        const response = await api.get(API.QR(shortUrl), {
          responseType: "blob",
        });

        objectUrl = URL.createObjectURL(response.data);
        setQrBlobUrl(objectUrl);
      } catch (error) {
        if (error?.response?.status === 404) {
          setNotFound(true);
        } else {
          setQrError("Could not load QR code.");
        }
      } finally {
        setQrLoading(false);
      }
    };

    fetchQr();

    return () => {
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [shortUrl]);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(fullShortUrl);
      toast.success("Short URL copied.");
    } catch {
      toast.error("Could not copy the short URL.");
    }
  };

  if (qrLoading && previewLoading) {
    return <Loader fullPage message="Loading link details..." />;
  }

  if (notFound) {
    return (
      <div className="min-h-page flex items-center justify-center px-4">
        <EmptyState title="This link doesn't exist" subtitle="Please verify the short URL." />
      </div>
    );
  }

  return (
    <div className="min-h-page bg-slate-50 lg:px-14 sm:px-8 px-4 py-8">
      <div className="max-w-5xl mx-auto space-y-6">
        <section className="bg-white border border-slate-200 rounded-xl p-6 shadow-card">
          <div className="flex flex-col lg:flex-row lg:items-center gap-6">
            <div className="w-full lg:w-72 flex justify-center">
              {qrLoading ? (
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

              {previewError && (
                <p className="text-sm text-amber-700 mt-3">{previewError}</p>
              )}
            </div>
          </div>
        </section>

        <LinkPreviewCard preview={preview} fallbackUrl={fullShortUrl} />
      </div>
    </div>
  );
};

export default LinkDetailPage;
