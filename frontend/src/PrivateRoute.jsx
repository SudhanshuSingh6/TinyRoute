import { Navigate } from "react-router-dom";
import { useFetchProfile } from "./hooks/useQuery";
import Loader from "./components/Common/Loader";

export default function PrivateRoute({ children, publicPage = false }) {
  const { data: profile, isLoading, isError } = useFetchProfile();

  if (isLoading) {
    return <Loader fullPage message="Verifying access..." />;
  }

  const isAuthenticated = !!profile && !isError;

  if (publicPage) {
    return isAuthenticated ? <Navigate to="/dashboard" replace /> : children;
  }

  return isAuthenticated ? children : <Navigate to="/login" replace />;
}