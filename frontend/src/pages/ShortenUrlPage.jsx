import { useEffect } from "react";
import { useParams } from "react-router-dom";

const ShortenUrlPage = () => {
  const { url } = useParams();

  useEffect(() => {
    if (url) {
      window.location.href = import.meta.env.VITE_BACKEND_URL + `/${url}`;
    }
  }, [url]);

  return (
    <div className="min-h-screen flex items-center justify-center">
      <p className="text-slate-600 text-lg">Redirecting...</p>
    </div>
  );
};

export default ShortenUrlPage;
