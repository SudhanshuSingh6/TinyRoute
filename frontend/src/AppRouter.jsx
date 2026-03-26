import { Outlet, Route, Routes } from "react-router-dom";
import { Toaster } from "react-hot-toast";

import Navbar from "./components/common/Navbar";
import Footer from "./components/common/Footer";
import PrivateRoute from "./PrivateRoute";

// Pages
import LandingPage from "./pages/LandingPage";
import AboutPage from "./pages/AboutPage";
import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";
import DashboardLayout from "./pages/DashboardLayout";
import ShortenUrlPage from "./pages/ShortenUrlPage";
import ErrorPage from "./pages/ErrorPage";
import ProfilePage from "./pages/ProfilePage";
import AnalyticsPage from "./pages/AnalyticsPage";
import LinkHistoryPage from "./pages/LinkHistoryPage";
import LinkDetailPage from "./pages/LinkDetailPage";
import BioPage from "./pages/BioPage";

const MainLayout = () => {
  return (
    <>
      <Navbar />
      <Outlet />
      <Footer />
    </>
  );
};

const AppRouter = () => {
  return (
    <>
      <Toaster position="bottom-center" />

      <Routes>
        <Route path="/bio/:username" element={<BioPage />} />

        <Route element={<MainLayout />}>
          {/* Public routes */}
          <Route path="/" element={<LandingPage />} />
          <Route path="/about" element={<AboutPage />} />
          <Route path="/link/:shortUrl" element={<LinkDetailPage />} />

          {/* Auth routes */}
          <Route
            path="/register"
            element={
              <PrivateRoute publicPage={true}>
                <RegisterPage />
              </PrivateRoute>
            }
          />
          <Route
            path="/login"
            element={
              <PrivateRoute publicPage={true}>
                <LoginPage />
              </PrivateRoute>
            }
          />

          {/* Protected routes */}
          <Route
            path="/dashboard"
            element={
              <PrivateRoute publicPage={false}>
                <DashboardLayout />
              </PrivateRoute>
            }
          />
          <Route
            path="/profile"
            element={
              <PrivateRoute publicPage={false}>
                <ProfilePage />
              </PrivateRoute>
            }
          />
          <Route
            path="/analytics/:shortUrl"
            element={
              <PrivateRoute publicPage={false}>
                <AnalyticsPage />
              </PrivateRoute>
            }
          />
          <Route
            path="/history/:id"
            element={
              <PrivateRoute publicPage={false}>
                <LinkHistoryPage />
              </PrivateRoute>
            }
          />

          <Route path="/error" element={<ErrorPage />} />
          <Route
            path="*"
            element={
              <ErrorPage message="We can't seem to find the page you're looking for." />
            }
          />
        </Route>
      </Routes>
    </>
  );
};

export default AppRouter;

export const SubDomainRouter = () => {
  return (
    <Routes>
      <Route path="/:url" element={<ShortenUrlPage />} />
    </Routes>
  );
};
