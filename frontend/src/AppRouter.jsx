import { Route, Routes } from "react-router-dom";
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

const AppRouter = () => {
  return (
    <>
      <Navbar />
      <Toaster position="bottom-center" />
      <Routes>
        {/* Public routes */}
        <Route path="/" element={<LandingPage />} />
        <Route path="/about" element={<AboutPage />} />

        {/* Auth routes — redirect to dashboard if already logged in */}
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
        <Route path="/error" element={<ErrorPage />} />
        <Route
          path="*"
          element={
            <ErrorPage message="We can't seem to find the page you're looking for." />
          }
        />
      </Routes>
      <Footer />
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
