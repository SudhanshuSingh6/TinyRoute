import { queryClient } from "../main";

/**
 * Logout user: invalidate cached queries and redirect to login.
 *
 * The accessToken/refreshToken cookies are HttpOnly, so JavaScript can't clear
 * them here — they're revoked and cleared server-side by POST /api/auth/logout
 * (see Navbar), which is called before this runs.
 */
export const handleLogout = async () => {
  // Clear all queries from cache
  queryClient.clear();

  // Redirect to login
  window.location.href = "/login";
};
