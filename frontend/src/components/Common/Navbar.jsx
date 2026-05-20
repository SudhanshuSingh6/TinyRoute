import { useEffect, useRef, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { IoIosMenu } from "react-icons/io";
import { RxCross2 } from "react-icons/rx";
import { ChevronDown, Eye, LogOut, User } from "lucide-react";

import { useStoreContext } from "../../contextApi/ContextApi";
import { useFetchProfile } from "../../hooks/useQuery";
import Logo from "./Logo";

const NAV_LINKS = [
  { label: "Home", path: "/" },
  { label: "About", path: "/about" },
];

const Navbar = () => {
  const navigate = useNavigate();
  const { token, setToken } = useStoreContext();
  const path = useLocation().pathname;

  const [navbarOpen, setNavbarOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const profileMenuRef = useRef(null);

  const { data: profile } = useFetchProfile(token);

  const username = profile?.username;
  const avatarUrl = profile?.avatarUrl;
  const publicProfilePath = username ? `/bio/${username}` : "/profile";
  const initials = username?.charAt(0)?.toUpperCase() || "U";

  const onLogOutHandler = () => {
    setToken(null);
    localStorage.removeItem("JWT_TOKEN");
    setProfileOpen(false);
    setNavbarOpen(false);
    navigate("/login");
  };

  const isActive = (linkPath) => path === linkPath;

  useEffect(() => {
    const handleClickOutside = (event) => {
      if (
        profileMenuRef.current &&
        !profileMenuRef.current.contains(event.target)
      ) {
        setProfileOpen(false);
      }
    };

    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  useEffect(() => {
    setProfileOpen(false);
    setNavbarOpen(false);
  }, [path]);

  return (
    <div className="h-16 bg-custom-gradient z-50 flex items-center sticky top-0 shadow-md">
      <div className="lg:px-14 sm:px-8 px-4 w-full flex justify-between items-center">
        <Link to="/">
          <Logo size="md" variant="light" />
        </Link>

        <div className="hidden sm:flex items-center gap-8">
          {NAV_LINKS.map(({ label, path: linkPath }) => (
            <Link
              key={label}
              to={linkPath}
              className={`font-medium transition-all duration-150 ${
                isActive(linkPath)
                  ? "text-white font-semibold"
                  : "text-gray-200 hover:text-white"
              }`}
            >
              {label}
            </Link>
          ))}

          {token && (
            <Link
              to="/dashboard"
              className={`font-medium transition-all duration-150 ${
                isActive("/dashboard")
                  ? "text-white font-semibold"
                  : "text-gray-200 hover:text-white"
              }`}
            >
              Dashboard
            </Link>
          )}

          {!token ? (
            <div className="flex items-center gap-2">
              <Link
                to="/login"
                className="text-gray-200 hover:text-white font-semibold px-4 py-1.5 rounded-md border border-white/30 hover:border-white/70 transition-all duration-150 text-sm"
              >
                Sign In
              </Link>
              <Link
                to="/register"
                className="bg-white text-btnColor font-semibold px-4 py-1.5 rounded-md hover:bg-slate-100 transition-all duration-150 text-sm"
              >
                Sign Up
              </Link>
            </div>
          ) : (
            <div className="relative" ref={profileMenuRef}>
              <button
                type="button"
                onClick={() => setProfileOpen((open) => !open)}
                className="flex items-center gap-2 rounded-md border border-white/30 px-2.5 py-1.5 text-sm font-semibold text-white hover:border-white/70 hover:bg-white/10 transition-all duration-150"
                aria-haspopup="menu"
                aria-expanded={profileOpen}
              >
                {avatarUrl ? (
                  <img
                    src={avatarUrl}
                    alt={username || "Profile"}
                    className="h-7 w-7 rounded-full object-cover"
                  />
                ) : (
                  <span className="flex h-7 w-7 items-center justify-center rounded-full bg-white text-btnColor text-xs font-bold">
                    {initials}
                  </span>
                )}

                <span className="max-w-28 truncate">
                  {username || "Profile"}
                </span>
                <ChevronDown
                  size={16}
                  className={`transition-transform ${
                    profileOpen ? "rotate-180" : ""
                  }`}
                />
              </button>

              {profileOpen && (
                <div className="absolute right-0 mt-2 w-48 overflow-hidden rounded-md bg-white shadow-lg ring-1 ring-black/10">
                  <Link
                    to={publicProfilePath}
                    className="flex items-center gap-2 px-4 py-2.5 text-sm font-medium text-slate-700 hover:bg-slate-100"
                  >
                    <Eye size={16} />
                    Public View
                  </Link>

                  <Link
                    to="/profile"
                    className="flex items-center gap-2 px-4 py-2.5 text-sm font-medium text-slate-700 hover:bg-slate-100"
                  >
                    <User size={16} />
                    Profile
                  </Link>

                  <button
                    type="button"
                    onClick={onLogOutHandler}
                    className="flex w-full items-center gap-2 px-4 py-2.5 text-left text-sm font-medium text-rose-600 hover:bg-rose-50"
                  >
                    <LogOut size={16} />
                    Logout
                  </button>
                </div>
              )}
            </div>
          )}
        </div>

        <button
          type="button"
          onClick={() => setNavbarOpen(!navbarOpen)}
          className="sm:hidden flex items-center"
        >
          {navbarOpen ? (
            <RxCross2 className="text-white text-3xl" />
          ) : (
            <IoIosMenu className="text-white text-3xl" />
          )}
        </button>
      </div>

      <div
        className={`sm:hidden absolute left-0 top-16 w-full bg-custom-gradient shadow-lg transition-all duration-200 overflow-hidden ${
          navbarOpen ? "max-h-[32rem] pb-5" : "max-h-0"
        }`}
      >
        <div className="flex flex-col gap-3 px-6 pt-4">
          {NAV_LINKS.map(({ label, path: linkPath }) => (
            <Link
              key={label}
              to={linkPath}
              className={`font-medium text-base ${
                isActive(linkPath)
                  ? "text-white font-semibold"
                  : "text-gray-200"
              }`}
            >
              {label}
            </Link>
          ))}

          {token && (
            <>
              <Link
                to="/dashboard"
                className={`font-medium text-base ${
                  isActive("/dashboard")
                    ? "text-white font-semibold"
                    : "text-gray-200"
                }`}
              >
                Dashboard
              </Link>

              <div className="mt-2 border-t border-white/20 pt-3">
                <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-white/70">
                  Profile
                </p>

                <div className="flex flex-col gap-2">
                  <Link
                    to={publicProfilePath}
                    className="flex items-center gap-2 text-gray-200 font-medium text-base"
                  >
                    <Eye size={16} />
                    Public View
                  </Link>

                  <Link
                    to="/profile"
                    className="flex items-center gap-2 text-gray-200 font-medium text-base"
                  >
                    <User size={16} />
                    Profile
                  </Link>

                  <button
                    type="button"
                    onClick={onLogOutHandler}
                    className="flex items-center gap-2 bg-rose-600 text-white font-semibold px-4 py-1.5 rounded-md text-sm w-fit"
                  >
                    <LogOut size={16} />
                    Logout
                  </button>
                </div>
              </div>
            </>
          )}

          {!token && (
            <>
              <Link
                to="/login"
                className="inline-block border border-white/40 text-gray-200 font-semibold px-4 py-1.5 rounded-md text-sm w-fit"
              >
                Sign In
              </Link>
              <Link
                to="/register"
                className="inline-block bg-white text-btnColor font-semibold px-4 py-1.5 rounded-md text-sm w-fit"
              >
                Sign Up
              </Link>
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default Navbar;
