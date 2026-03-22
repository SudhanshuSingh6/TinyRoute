import { useNavigate } from "react-router-dom";
import Card from "../components/common/Card";
import { useStoreContext } from "../contextApi/ContextApi";
import { FaLink, FaChartLine, FaShieldAlt, FaBolt } from "react-icons/fa";
import Button from "../components/common/Button";
import Logo from "../components/common/Logo";

const FEATURES = [
  { title: "Simple URL Shortening", desc: "Create short, memorable links in seconds. Our intuitive interface makes URL shortening effortless — no technical knowledge required.", icon: <FaLink /> },
  { title: "Powerful Analytics",    desc: "Gain insights into your link performance. Track clicks and date-based engagement to understand how your links are performing.",      icon: <FaChartLine /> },
  { title: "Enhanced Security",     desc: "All shortened URLs are protected with advanced encryption. Your data and your users' data remain safe and secure.",                  icon: <FaShieldAlt /> },
  { title: "Fast and Reliable",     desc: "Lightning-fast redirects with high uptime. Your shortened URLs will always be available and responsive for your users.",             icon: <FaBolt /> },
];

const STATS = [
  { value: "2.4k", label: "Clicks" },
  { value: "12",   label: "Links"  },
  { value: "↑98%", label: "Uptime", green: true },
];

const StatCard = ({ value, label, green }) => (
  <div className="flex-1 bg-slate-50 rounded-lg p-3 text-center">
    <p className={`font-bold text-lg ${green ? "text-green-500" : "text-slate-800"}`}>{value}</p>
    <p className="text-slate-400 text-xs">{label}</p>
  </div>
);

const MockDivider = () => (
  <div className="flex justify-center items-center gap-2 mb-3">
    <div className="h-px w-8 bg-slate-200" />
    <Logo size="sm" variant="dark" iconOnly />
    <div className="h-px w-8 bg-slate-200" />
  </div>
);

const LandingPage = () => {
  const navigate = useNavigate();
  const { token } = useStoreContext();

  const handleManageLinks = () => navigate(token ? "/dashboard" : "/login");
  const handleCreateLink  = () => navigate(token ? "/dashboard" : "/register");

  return (
    <div className="min-h-page lg:px-14 sm:px-8 px-4 bg-white">

      {/* Hero */}
      <div className="lg:flex-row flex-col lg:py-16 pt-14 lg:gap-14 gap-10 flex justify-between items-center">
        <div className="flex-1">
          <div className="inline-flex items-center gap-2 bg-blue-50 border border-blue-200 text-blue-600 text-xs font-semibold px-3 py-1.5 rounded-full mb-5">
            <span className="w-1.5 h-1.5 rounded-full bg-blue-500 animate-pulse" />
            Free URL Shortener
          </div>
          <h1 className="font-bold font-montserrat text-slate-900 md:text-5xl sm:text-4xl text-3xl md:leading-tight sm:leading-snug leading-10 lg:w-full md:w-3/4 w-full">
            Short links.{" "}
            <span className="bg-custom-gradient bg-clip-text text-transparent">Real Insights...</span>
          </h1>
          <p className="text-slate-500 text-base my-6 leading-relaxed lg:w-5/6">
            TinyRoute turns long, unwieldy links into clean, shareable URLs.
            Track clicks, manage all your links in one place, and share with confidence.
          </p>
          <div className="flex items-center gap-3 flex-wrap">
            <Button variant="primary" size="lg" onClick={handleManageLinks}>Manage Links</Button>
            <Button variant="secondary" size="lg" onClick={handleCreateLink}>Create Short Link</Button>
          </div>
          <p className="text-slate-400 text-xs mt-5">Free to use · No credit card required · Instant setup</p>
        </div>

        <div className="flex-1 flex justify-center w-full">
          <div className="relative">
            <div className="absolute inset-0 bg-custom-gradient opacity-10 rounded-3xl blur-3xl scale-110" />
            <div className="relative bg-white border border-slate-200 rounded-2xl shadow-xl p-6 sm:w-card-md w-card-sm">
              <div className="flex gap-1.5 mb-5">
                <span className="w-3 h-3 rounded-full bg-red-400" />
                <span className="w-3 h-3 rounded-full bg-yellow-400" />
                <span className="w-3 h-3 rounded-full bg-green-400" />
              </div>
              <div className="bg-slate-50 border border-slate-200 rounded-lg px-3 py-2.5 mb-3">
                <p className="text-slate-400 text-xs truncate">
                  https://www.example.com/very/long/url/that/nobody/wants/to/share
                </p>
              </div>
              <MockDivider />
              <div className="bg-blue-50 border border-blue-200 rounded-lg px-3 py-2.5 flex items-center justify-between">
                <p className="text-blue-600 text-sm font-semibold">url.tinyroute.com/xK9mP</p>
                <span className="text-xs bg-blue-500 text-white px-2 py-0.5 rounded-md font-medium">Copy</span>
              </div>
              <div className="flex gap-3 mt-4">
                {STATS.map((s) => <StatCard key={s.label} {...s} />)}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Features */}
      <div className="sm:pt-20 pt-12 pb-16">
        <div className="text-center mb-10">
          <p className="text-blue-500 font-semibold text-sm uppercase tracking-widest mb-2">Why TinyRoute?</p>
          <h2 className="text-slate-900 font-bold font-montserrat lg:w-1/2 md:w-2/3 sm:w-3/4 w-full mx-auto text-3xl leading-snug">
            Everything you need to manage your links
          </h2>
        </div>
        <div className="grid lg:gap-6 gap-4 xl:grid-cols-4 lg:grid-cols-2 sm:grid-cols-2 grid-cols-1">
          {FEATURES.map(({ title, desc, icon }) => (
            <Card key={title} title={title} desc={desc} icon={icon} />
          ))}
        </div>
      </div>

      {/* CTA Banner */}
      <div className="bg-custom-gradient rounded-2xl p-10 text-center text-white mb-16">
        <h2 className="font-bold font-montserrat text-3xl mb-3">Ready to shorten your first link?</h2>
        <p className="text-blue-100 mb-6 text-sm">Join thousands of users simplifying how they share links online.</p>
        <div className="flex justify-center">
          <Button variant="ghost" size="lg" onClick={handleCreateLink} className="bg-white text-btnColor hover:bg-slate-100 font-bold">
            Get Started — It&apos;s Free
          </Button>
        </div>
      </div>

    </div>
  );
};

export default LandingPage;
