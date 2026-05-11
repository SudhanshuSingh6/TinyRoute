import { useMemo } from "react";
import dayjs from "dayjs";
import { useNavigate, useParams } from "react-router-dom";
import { FaHistory } from "react-icons/fa";
import Loader from "../components/common/Loader";
import EmptyState from "../components/common/EmptyState";
import { useStoreContext } from "../contextApi/ContextApi";
import { useFetchLinkHistory } from "../hooks/useQuery";

const LinkHistoryPage = () => {
  const { shortUrl } = useParams();
  const navigate = useNavigate();
  const { token } = useStoreContext();

  const onError = () => {
    navigate("/error");
  };

  const { isLoading, data: history = [] } = useFetchLinkHistory(token, shortUrl, onError);

  const sortedHistory = useMemo(() => {
    return [...history].sort(
      (a, b) => new Date(b.changedAt).getTime() - new Date(a.changedAt).getTime()
    );
  }, [history]);

  if (isLoading) {
    return <Loader fullPage message="Loading link history..." />;
  }

  return (
    <div className="min-h-page bg-slate-50 lg:px-14 sm:px-8 px-4 py-8">
      <div className="max-w-4xl mx-auto bg-white border border-slate-200 rounded-xl p-6 shadow-card">
        <h1 className="text-2xl font-bold text-slate-900 mb-1">Link Edit History</h1>
        <p className="text-slate-500 text-sm mb-6">Tracking changes for link: {shortUrl}</p>

        {sortedHistory.length === 0 ? (
          <EmptyState
            icon={<FaHistory />}
            title="No edits yet"
            subtitle="This link has not been edited so far."
          />
        ) : (
          <div className="relative border-l-2 border-slate-200 ml-2 pl-6">
            {sortedHistory.map((item, index) => (
              <div key={`${item.changedAt}-${index}`} className="mb-6 last:mb-0 relative">
                <span className="absolute -left-[30px] top-1 w-3 h-3 rounded-full bg-btnColor" />
                <p className="text-xs text-slate-500 font-medium mb-1">
                  {dayjs(item.changedAt).format("MMM DD, YYYY hh:mm A")}
                </p>
                <p className="text-slate-800 break-all">{item.oldUrl}</p>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default LinkHistoryPage;
