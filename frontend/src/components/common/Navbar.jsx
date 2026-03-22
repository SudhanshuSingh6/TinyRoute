import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { IoIosMenu } from "react-icons/io";
import { RxCross2 } from "react-icons/rx";
import { useStoreContext } from "../../contextApi/ContextApi";
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

  const onLogOutHandler = () => {
    setToken(null);
    localStorage.removeItem("JWT_TOKEN");
    navigate("/login");
  };

  const isActive = (linkPath) => path === linkPath;

  return (
    <div className="h-16 bg-custom-gradient z-50 flex items-center sticky top-0 shadow-md">
      <div className="lg:px-14 sm:px-8 px-4 w-full flex justify-between items-center">

        {/* Logo */}
        <Link to="/">
          <Logo size="md" variant="light" />
        </Link>

        {/* ── Desktop nav ── */}
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
            <button
              onClick={onLogOutHandler}
              className="bg-rose-600 text-white font-semibold px-4 py-1.5 rounded-md hover:bg-rose-700 transition-all duration-150 text-sm"
            >
              Log Out
            </button>
          )}
        </div>

        {/* ── Mobile hamburger ── */}
        <button
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

      {/* ── Mobile dropdown ── */}
      <div
        className={`sm:hidden absolute left-0 top-16 w-full bg-custom-gradient shadow-lg transition-all duration-200 overflow-hidden ${
          navbarOpen ? "max-h-72 pb-5" : "max-h-0"
        }`}
      >
        <div className="flex flex-col gap-3 px-6 pt-4">
          {NAV_LINKS.map(({ label, path: linkPath }) => (
            <Link
              key={label}
              to={linkPath}
              onClick={() => setNavbarOpen(false)}
              className={`font-medium text-base ${
                isActive(linkPath) ? "text-white font-semibold" : "text-gray-200"
              }`}
            >
              {label}
            </Link>
          ))}

          {token && (
            <Link
              to="/dashboard"
              onClick={() => setNavbarOpen(false)}
              className={`font-medium text-base ${
                isActive("/dashboard") ? "text-white font-semibold" : "text-gray-200"
              }`}
            >
              Dashboard
            </Link>
          )}

          {!token ? (
            <>
              <Link
                to="/login"
                onClick={() => setNavbarOpen(false)}
                className="inline-block border border-white/40 text-gray-200 font-semibold px-4 py-1.5 rounded-md text-sm w-fit"
              >
                Sign In
              </Link>
              <Link
                to="/register"
                onClick={() => setNavbarOpen(false)}
                className="inline-block bg-white text-btnColor font-semibold px-4 py-1.5 rounded-md text-sm w-fit"
              >
                Sign Up
              </Link>
            </>
          ) : (
            <button
              onClick={() => { onLogOutHandler(); setNavbarOpen(false); }}
              className="bg-rose-600 text-white font-semibold px-4 py-1.5 rounded-md text-sm w-fit"
            >
              Log Out
            </button>
          )}
        </div>
      </div>
    </div>
  );
};

export default Navbar;