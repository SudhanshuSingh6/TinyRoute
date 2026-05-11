import { useCallback, useMemo, useState } from "react";
import { useParams } from "react-router-dom";
import EmptyState from "../components/common/EmptyState";
import QRCodeDisplay from "../components/link/QRCodeDisplay";
import LinkPreviewCard from "../components/link/LinkPreviewCard";
import { useFetchLinkPreview } from "../hooks/useQuery";

const LinkDetailPage = () => {
  const { shortUrl } = useParams();
  const [notFound, setNotFound] = useState(false);
  const [previewError, setPreviewError] = useState("");

  const fullShortUrl = useMemo(
    () => `${import.meta.env.VITE_REACT_SUBDOMAIN}/${shortUrl}`,
    [shortUrl]
  );

  const handleNotFound = useCallback(() => {
    setNotFound(true);
  }, []);

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
        <QRCodeDisplay shortUrl={shortUrl} onNotFound={handleNotFound} />

        <LinkPreviewCard
          preview={preview}
          fallbackUrl={fullShortUrl}
          loading={previewLoading}
          error={previewError}
        />
      </div>
    </div>
  );
};

export default LinkDetailPage;
