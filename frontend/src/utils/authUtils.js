import { queryClient } from "../main";

/**
 * Logout user: invalidate cache, clear cookies, redirect to login
 */
export const handleLogout = async () => {
  // Clear all queries from cache
  queryClient.clear();

  // Clear cookies
  document.cookie = "accessToken=; path=/; max-age=0";
  document.cookie = "refreshToken=; path=/; max-age=0";

  // Redirect to login
  window.location.href = "/login";
};
