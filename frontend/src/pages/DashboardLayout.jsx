import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { FaLink } from "react-icons/fa";
import Graph from "../components/dashboard/Graph";
import ShortenPopUp from "../components/dashboard/ShortenPopUp";
import ShortenUrlList from "../components/dashboard/ShortenUrlList";
import Loader from "../components/common/Loader";
import EmptyState from "../components/common/EmptyState";
import Button from "../components/common/Button";
import DateRangePicker from "../components/common/DateRangePicker";
import { useStoreContext } from "../contextApi/ContextApi";
import { useFetchMyShortUrls, useFetchTotalClicks } from "../hooks/useQuery";

const DashboardLayout = () => {
  const { token } = useStoreContext();
  const navigate = useNavigate();
  const [shortenPopUp, setShortenPopUp] = useState(false);

  const currentYear = new Date().getFullYear();
  const [startDate, setStartDate] = useState(`${currentYear}-01-01`);
  const [endDate, setEndDate] = useState(`${currentYear}-12-31`);

  function onError() {
    navigate("/error");
  }

  const {
    isLoading,
    data: myShortenUrls = [],
    refetch,
  } = useFetchMyShortUrls(token, onError);

  const { isLoading: loader, data: totalClicks = [] } = useFetchTotalClicks(
    token,
    onError,
    startDate,
    endDate
  );

  const onDateChange = (start, end) => {
    setStartDate(start);
    setEndDate(end);
  };

  if (loader) {
    return <Loader fullPage message="Loading your dashboard..." />;
  }

  return (
    <div className="lg:px-14 sm:px-8 px-4 min-h-page">
      <div className="lg:w-11/12 w-full mx-auto py-16">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 mb-4">
          <h1 className="text-slate-900 font-bold text-2xl">Dashboard</h1>
          <DateRangePicker
            startDate={startDate}
            endDate={endDate}
            onChange={onDateChange}
            type="date"
            label="Clicks Range"
          />
        </div>

        <div className="h-96 relative">
          {totalClicks.length === 0 && (
            <div className="absolute flex flex-col justify-center sm:items-center items-end w-full left-0 top-0 bottom-0 right-0 m-auto pointer-events-none">
              <h1 className="text-slate-800 font-montserrat sm:text-2xl text-18 font-bold mb-1">
                No Data For This Time Period
              </h1>
              <p className="sm:w-96 w-[90%] sm:ml-0 pl-6 text-center sm:text-base text-sm text-slate-500">
                Share your short links to start seeing click data here.
              </p>
            </div>
          )}
          <Graph graphData={totalClicks} />
        </div>

        <div className="py-5 sm:text-end text-center">
          <Button
            variant="primary"
            size="md"
            onClick={() => setShortenPopUp(true)}
          >
            + Create a New Short URL
          </Button>
        </div>

        <div>
          {isLoading ? (
            <Loader message="Loading your links..." />
          ) : myShortenUrls.length === 0 ? (
            <EmptyState
              icon={<FaLink />}
              title="No short links yet"
              subtitle="Create your first short URL and start tracking clicks."
              actionLabel="Create Short URL"
              onAction={() => setShortenPopUp(true)}
            />
          ) : (
            <ShortenUrlList data={myShortenUrls} refetch={refetch} />
          )}
        </div>
      </div>

      <ShortenPopUp
        refetch={refetch}
        open={shortenPopUp}
        setOpen={setShortenPopUp}
      />
    </div>
  );
};

export default DashboardLayout;
