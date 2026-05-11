import PropTypes from "prop-types";
import { FaExternalLinkAlt } from "react-icons/fa";
import Loader from "../common/Loader";

const LinkPreviewCard = ({ preview, fallbackUrl, loading, error }) => {
  if (loading) {
    return (
      <section className="bg-white border border-slate-200 rounded-xl p-6 shadow-card">
        <Loader message="Loading link preview..." />
      </section>
    );
  }

  const title = preview?.title ?? preview?.ogTitle ?? "";
  const description = preview?.description ?? preview?.ogDescription ?? "";
  const image = preview?.image ?? preview?.ogImage ?? preview?.imageUrl ?? "";
  const sourceUrl = preview?.url ?? preview?.originalUrl ?? fallbackUrl;

  const hasMetadata = Boolean(title || description || image);

  return (
    <section className="bg-white border border-slate-200 rounded-xl p-6 shadow-card">
      <h2 className="text-lg font-bold text-slate-900 mb-4">Link Preview</h2>

      {error && <p className="text-sm text-amber-700 mb-3">{error}</p>}

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

LinkPreviewCard.propTypes = {
  preview: PropTypes.object,
  fallbackUrl: PropTypes.string.isRequired,
  loading: PropTypes.bool,
  error: PropTypes.string,
};

LinkPreviewCard.defaultProps = {
  preview: null,
  loading: false,
  error: "",
};

export default LinkPreviewCard;
