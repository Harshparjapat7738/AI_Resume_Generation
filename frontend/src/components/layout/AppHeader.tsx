import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { logout as logoutRequest } from '@/services/authApi';
import { useSession, useSessionActions } from '@/services/session';
import { GridIcon, SparkleIcon, UserIcon } from './icons';
import { UserMenu } from './UserMenu';

const navLinks = [
  { to: '/dashboard', label: 'Dashboard', icon: GridIcon },
  { to: '/profile', label: 'Profile', icon: UserIcon },
];

/** Persistent top bar for every authenticated app screen — the marketing SiteHeader
 *  (landing-only) mirrors this same nav + account menu when a session is present, so
 *  the chrome reads as one navbar no matter which page it's mounted on. */
export function AppHeader() {
  const { data: user } = useSession();
  const { clearSession } = useSessionActions();
  const location = useLocation();
  const [scrolled, setScrolled] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  // Close a still-open mobile menu on route changes (e.g. after tapping a link).
  useEffect(() => {
    setMenuOpen(false);
  }, [location.pathname]);

  const handleLogout = async () => {
    setMenuOpen(false);
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
    <header
      className={`sticky top-0 z-50 border-b transition-colors duration-300 ${
        scrolled
          ? 'border-border bg-void/80 backdrop-blur-md'
          : 'border-transparent bg-void'
      }`}
    >
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-6">
        <Link to="/" className="flex items-center gap-2">
          <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-surface-2 text-ember-soft ring-1 ring-border-strong">
            <SparkleIcon className="h-4 w-4" />
          </span>
          <span className="text-base font-semibold tracking-tight text-ink">
            CareerForge <span className="text-gradient">AI</span>
          </span>
        </Link>

        {user && (
          <>
            <nav aria-label="Primary" className="hidden items-center gap-1 md:flex">
              {navLinks.map((link) => {
                const isActive =
                  location.pathname === link.to || location.pathname.startsWith(`${link.to}/`);
                return (
                  <Link
                    key={link.to}
                    to={link.to}
                    aria-current={isActive ? 'page' : undefined}
                    className={`rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                      isActive ? 'text-ink' : 'text-ink-muted hover:text-ink'
                    }`}
                  >
                    {link.label}
                  </Link>
                );
              })}
            </nav>

            <div className="hidden items-center gap-4 md:flex">
              <Link
                to="/generate"
                className="rounded-full bg-linear-to-r from-ember-soft to-rose px-4 py-2 text-sm font-semibold text-void transition-transform hover:-translate-y-0.5"
              >
                Generate
              </Link>
              <UserMenu user={user} onLogout={handleLogout} />
            </div>

            <button
              type="button"
              onClick={() => setMenuOpen((open) => !open)}
              className="inline-flex h-9 w-9 items-center justify-center rounded-lg border border-border text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember-soft md:hidden"
              aria-expanded={menuOpen}
              aria-label="Toggle navigation menu"
            >
              <span className="relative block h-3.5 w-4">
                <span
                  className={`absolute left-0 h-px w-4 bg-current transition-transform duration-200 ${
                    menuOpen ? 'top-1.5 rotate-45' : 'top-0'
                  }`}
                />
                <span
                  className={`absolute left-0 top-1.5 h-px w-4 bg-current transition-opacity duration-200 ${
                    menuOpen ? 'opacity-0' : 'opacity-100'
                  }`}
                />
                <span
                  className={`absolute left-0 h-px w-4 bg-current transition-transform duration-200 ${
                    menuOpen ? 'top-1.5 -rotate-45' : 'top-3'
                  }`}
                />
              </span>
            </button>
          </>
        )}
      </div>

      {user && menuOpen && (
        <nav aria-label="Primary" className="animate-menu-in border-t border-border bg-void md:hidden">
          <ul className="flex flex-col gap-1 px-6 py-4">
            {navLinks.map((link) => {
              const isActive =
                location.pathname === link.to || location.pathname.startsWith(`${link.to}/`);
              return (
                <li key={link.to}>
                  <Link
                    to={link.to}
                    aria-current={isActive ? 'page' : undefined}
                    className={`block rounded-lg px-3 py-2 text-sm font-medium ${
                      isActive ? 'bg-surface-2 text-ink' : 'text-ink-muted hover:text-ink'
                    }`}
                  >
                    {link.label}
                  </Link>
                </li>
              );
            })}
            <li>
              <Link
                to="/generate"
                className="mt-1 block rounded-full bg-linear-to-r from-ember-soft to-rose px-4 py-2 text-center text-sm font-semibold text-void"
              >
                Generate
              </Link>
            </li>
            <li className="mt-2 flex items-center justify-between gap-3 border-t border-border px-3 pt-3">
              <span className="truncate text-sm text-ink-faint">{user.email}</span>
              <button
                type="button"
                onClick={handleLogout}
                className="shrink-0 text-sm font-medium text-ink-muted hover:text-ink"
              >
                Log out
              </button>
            </li>
          </ul>
        </nav>
      )}
    </header>
  );
}
