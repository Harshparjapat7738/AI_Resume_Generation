import { logout as logoutRequest } from '@/services/authApi';
import { useSessionActions } from '@/services/session';

/** Same logout sequence AppHeader/DashboardPage already use — the cookie is httpOnly and
 *  server-revoked whether or not the request itself succeeds, so the local session is always
 *  cleared, and a hard navigation (not react-router's navigate()) avoids a ProtectedRoute
 *  render race against the now-stale session query. Shared here so every dedicated data page
 *  doesn't redefine it. */
export function useLogout() {
  const { clearSession } = useSessionActions();
  return async () => {
    try {
      await logoutRequest();
    } catch {
      // Clear the local session regardless — see comment above.
    }
    clearSession();
    window.location.assign('/');
  };
}

