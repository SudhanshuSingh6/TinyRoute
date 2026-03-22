import dayjs from "dayjs";
import { useEffect, useState } from "react";
import CopyToClipboard from "react-copy-to-clipboard";
import { FaExternalLinkAlt, FaRegCalendarAlt } from "react-icons/fa";
import { IoCopy } from "react-icons/io5";
import { LiaCheckSolid } from "react-icons/lia";
import { MdAnalytics, MdOutlineAdsClick } from "react-icons/md";
import { Hourglass } from "react-loader-spinner";
import { useNavigate } from "react-router-dom";
import api from "../../api/api";
import { useStoreContext } from "../../contextApi/ContextApi";
import Graph from "./Graph";

const ActionButton = ({ onClick, color, children }) => (
  <div
    onClick={onClick}
    className={`flex cursor-pointer gap-1 items-center ${color} py-2 font-semibold shadow-md shadow-slate-500 px-6 rounded-md text-white`}
  >
    {children}
  </div>
);

const NoDataOverlay = () => (
  <div className="absolute flex flex-col justify-center sm:items-center items-end w-full inset-0 m-auto pointer-events-none">
    <h1 className="text-slate-800 font-montserrat sm:text-2xl text-17 font-bold mb-1">
      No Data For This Time Period
    </h1>
    <p className="sm:w-96 w-[90%] sm:ml-0 pl-6 text-center sm:text-base text-xs text-slate-500">
      Share your short link to start tracking clicks.
    </p>
  </div>
);

const ShortenItem = ({ originalUrl, shortUrl, clickCount, createdDate }) => {
  const { token } = useStoreContext();
  const navigate = useNavigate();
  const [isCopied, setIsCopied]         = useState(false);
  const [analyticToggle, setAnalyticToggle] = useState(false);
  const [loader, setLoader]             = useState(false);
  const [selectedUrl, setSelectedUrl]   = useState("");
  const [analyticsData, setAnalyticsData] = useState([]);

  const subDomain = import.meta.env.VITE_REACT_SUBDOMAIN.replace(/^https?:\/\//, "");
  const fullShortUrl = `${import.meta.env.VITE_REACT_SUBDOMAIN}/${shortUrl}`;

  const analyticsHandler = () => {
    if (!analyticToggle) setSelectedUrl(shortUrl);
    setAnalyticToggle(!analyticToggle);
  };

  const fetchAnalytics = async () => {
    setLoader(true);
    try {
      const { data } = await api.get(
        `/api/urls/analytics/${selectedUrl}?startDate=2024-12-01T00:00:00&endDate=2024-12-31T23:59:59`,
        { headers: { "Content-Type": "application/json", Accept: "application/json", Authorization: "Bearer " + token } }
      );
      setAnalyticsData(data);
      setSelectedUrl("");
    } catch {
      navigate("/error");
    } finally {
      setLoader(false);
    }
  };

  useEffect(() => {
    if (selectedUrl) fetchAnalytics();
  }, [selectedUrl]);

  return (
    <div className="bg-slate-100 shadow-lg border border-dotted border-slate-500 px-6 sm:py-1 py-3 rounded-md transition-all duration-100">
      <div className="flex sm:flex-row flex-col sm:justify-between w-full sm:gap-0 gap-5 py-5">

        {/* URL info */}
        <div className="flex-1 sm:space-y-1 max-w-full overflow-x-auto overflow-y-hidden">
          <div className="flex items-center gap-2 pb-1 sm:pb-0">
            <a href={fullShortUrl} target="_blank" rel="noreferrer"
              className="text-17 font-montserrat font-semibold text-linkColor">
              {subDomain}/{shortUrl}
            </a>
            <FaExternalLinkAlt className="text-linkColor text-sm" />
          </div>
          <p className="text-slate-700 font-normal text-17">{originalUrl}</p>
          <div className="flex items-center gap-8 pt-6">
            <div className="flex gap-1 items-center font-semibold text-green-800">
              <MdOutlineAdsClick className="text-22 me-1" />
              <span className="text-base">{clickCount}</span>
              <span className="text-17">{clickCount <= 1 ? "Click" : "Clicks"}</span>
            </div>
            <div className="flex items-center gap-2 font-semibold text-slate-800">
              <FaRegCalendarAlt />
              <span className="text-17">{dayjs(createdDate).format("MMM DD, YYYY")}</span>
            </div>
          </div>
        </div>

        {/* Actions */}
        <div className="flex flex-1 sm:justify-end items-center gap-4">
          <CopyToClipboard onCopy={() => setIsCopied(true)} text={fullShortUrl}>
            <ActionButton color="bg-btnColor">
              <button>{isCopied ? "Copied" : "Copy"}</button>
              {isCopied ? <LiaCheckSolid /> : <IoCopy />}
            </ActionButton>
          </CopyToClipboard>

          <ActionButton color="bg-rose-700" onClick={analyticsHandler}>
            <button>Analytics</button>
            <MdAnalytics />
          </ActionButton>
        </div>
      </div>

      {/* Inline analytics panel */}
      <div className={`${analyticToggle ? "flex" : "hidden"} max-h-96 sm:mt-0 mt-5 min-h-96 relative border-t-2 w-full overflow-hidden`}>
        {loader ? (
          <div className="min-h-96 flex justify-center items-center w-full">
            <div className="flex flex-col items-center gap-2">
              <Hourglass visible height="50" width="50" colors={["#306cce", "#72a1ed"]} />
              <p className="text-slate-600 text-sm">Please wait...</p>
            </div>
          </div>
        ) : (
          <>
            {analyticsData.length === 0 && <NoDataOverlay />}
            <Graph graphData={analyticsData} />
          </>
        )}
      </div>
    </div>
  );
};

export default ShortenItem;
