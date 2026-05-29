import PropTypes from "prop-types";
import { FaChartBar, FaDownload, FaExternalLinkAlt } from "react-icons/fa";
import toast from "react-hot-toast";
import { useFetchLinkPreview } from "../../hooks/useQuery";
import Graph from "./Graph";

const ShortenItemExpanded = ({ shortUrl, clickCount }) => {
  const { isLoading: previewLoader, data: previewData } = useFetchLinkPreview({
    shortUrl,
    onError: () => toast.error("Could not load link preview."),
    enabled: true,
  });

  return (
    <div className="mt-6 grid grid-cols-1 gap-4 pb-6 md:grid-cols-2 xl:grid-cols-[0.9fr_1fr_340px]">
      {/* Total Clicks */}
      <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-sm font-medium text-slate-500">Total Clicks</p>
            <h2 className="mt-2 text-5xl font-bold text-slate-900">{clickCount ?? 0}</h2>
          </div>
          <div className="rounded-xl bg-violet-100 p-3 text-violet-700">
            <FaChartBar className="text-xl" />
          </div>
        </div>
        <div className="mt-6 h-52">
          <Graph graphData={[{ clickDate: "Clicks", count: clickCount || 0 }]} />
        </div>
        <div className="mt-6 rounded-xl bg-slate-50 p-4">
          <p className="text-sm text-slate-600">
            This is the total number of clicks received by this short link.
          </p>
        </div>
      </div>

      {/* Link Preview */}
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
                src={previewData?.imageUrl || "https://placehold.co/600x300?text=Preview"}
                alt={previewData?.title}
                className="h-56 w-full object-cover"
              />
            </div>
            <div className="mt-5">
              <h4 className="line-clamp-2 text-2xl font-bold text-slate-900">
                {previewData?.title || "No title available"}
              </h4>
              <p className="mt-3 line-clamp-4 text-sm leading-7 text-slate-600">
                {previewData?.description || "No description available for this link."}
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

      {/* QR Code */}
      <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <h3 className="text-xl font-bold text-slate-900">QR Code</h3>
        <p className="mt-1 text-sm text-slate-500">Scan to open this link</p>
        <div className="mx-auto mt-6 aspect-square w-full max-w-[260px] overflow-hidden rounded-2xl border border-slate-200 bg-white p-4">
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
  );
};

ShortenItemExpanded.propTypes = {
  shortUrl: PropTypes.string.isRequired,
  clickCount: PropTypes.number.isRequired,
};

export default ShortenItemExpanded;
