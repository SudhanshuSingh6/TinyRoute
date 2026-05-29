import { useMemo } from "react";
import { Link } from "react-router-dom";
import { FaExternalLinkAlt } from "react-icons/fa";
import Loader from "../components/Common/Loader";
import Button from "../components/Common/Button";
import StatBlock from "../components/Common/StatBlock";
import { useFetchProfile, useFetchMyShortUrls } from "../hooks/useQuery";
import { getInitials } from "../utils/helper";
import ProfileHero from "../components/Profile/ProfileHero";
import ProfileLinksSection from "../components/Profile/ProfileLinksSection";

const ProfilePage = () => {
  const {
    isLoading: profileLoading,
    data: profile,
    refetch: refetchProfile,
  } = useFetchProfile();

  const {
    isLoading: linksLoading,
    data: links = [],
    refetch: refetchLinks,
  } = useFetchMyShortUrls(() => {});

  const username = profile?.username ?? "User";
  const initials = useMemo(() => getInitials(username), [username]);
  const totalClicks = useMemo(
    () => links.reduce((s, l) => s + (l.clickCount ?? 0), 0),
    [links],
  );

  if (profileLoading) return <Loader fullPage message="Loading profile…" />;

  if (!profile) {
    return (
      <div className="min-h-page flex items-center justify-center px-4">
        <p className="text-slate-500">Profile data is not available.</p>
      </div>
    );
  }

  return (
    <div className="min-h-page bg-slate-50 lg:px-14 sm:px-8 px-4 py-8">
      <div className="max-w-4xl mx-auto space-y-6">
        <ProfileHero profile={profile} initials={initials} onSaved={refetchProfile} />

        <div className="grid grid-cols-3 gap-4">
          <StatBlock label="Total Links" value={links.length} color="blue" />
          <StatBlock
            label="Total Clicks"
            value={totalClicks.toLocaleString()}
            color="green"
          />
          <StatBlock
            label="Bio Views"
            value={profile.bioPageViews ?? 0}
            color="purple"
          />
        </div>

        <ProfileLinksSection
          links={links}
          isLoading={linksLoading}
          totalClicks={totalClicks}
          refetch={refetchLinks}
        />

        <div className="bg-white border border-slate-200 rounded-xl shadow-card px-5 py-4 flex items-center justify-between gap-4">
          <div>
            <p className="text-sm font-semibold text-slate-700">
              Your public bio page
            </p>
            <p className="text-xs text-slate-400 mt-0.5">
              Anyone can view this — it shows your bio and links.
            </p>
          </div>
          <Link to={`/bio/${username}`} target="_blank" rel="noreferrer">
            <Button variant="secondary" size="sm">
              <FaExternalLinkAlt className="text-xs" /> View
            </Button>
          </Link>
        </div>
      </div>
    </div>
  );
};

export default ProfilePage;
