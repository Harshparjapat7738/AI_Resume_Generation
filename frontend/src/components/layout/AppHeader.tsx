import { Link } from 'react-router-dom';
import { logout as logoutRequest } from '@/services/authApi';
import { useSession, useSessionActions } from '@/services/session';

/** Simple top bar for the authenticated app screens — the marketing SiteHeader is landing-only. */
export function AppHeader() {
  const { data: user } = useSession();
  const { clearSession } = useSessionActions();

  const handleLogout = async () => {
    try {
      await logoutRequest();
    } catch {
      // Clear the local session regardless — the cookie is httpOnly and server-revoked either way.
    }
    clearSession();
    // A real navigation, not react-router's navigate(): logging out from a protected page
    // (e.g. /profile) races an SPA navigate('/') against ProtectedRoute's own reactive
    // redirect to /login — both fire from the same session-clearing state update, and
    // React 18 batches them into one render, so call order between navigate() and
    // clearSession() doesn't actually control which one wins. A hard navigation sidesteps
    // React Router entirely and always lands on the public landing page.
    window.location.assign('/');
  };

  return (
    <header className="border-b border-border bg-void">
      <div className="mx-auto flex max-w-4xl items-center justify-between px-6 py-4">
        <Link to="/" className="text-base font-semibold tracking-tight text-ink">
          CareerForge <span className="text-gradient">AI</span>
        </Link>
        {user && (
          <nav className="flex items-center gap-5">
            <Link to="/dashboard" className="hidden text-sm text-ink-muted transition-colors hover:text-ink sm:inline">
              Dashboard
            </Link>
            <Link to="/profile" className="hidden text-sm text-ink-muted transition-colors hover:text-ink sm:inline">
              Profile
            </Link>
            <span className="hidden text-sm text-ink-faint md:inline">{user.email}</span>
            <button
              type="button"
              onClick={handleLogout}
              className="text-sm text-ink-muted transition-colors hover:text-ink"
            >
              Log out
            </button>
          </nav>
        )}
      </div>
    </header>
  );
}
