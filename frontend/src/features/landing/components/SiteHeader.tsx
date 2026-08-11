import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { GridIcon, UserIcon } from '@/components/layout/icons';
import { UserMenu } from '@/components/layout/UserMenu';
import { logout as logoutRequest } from '@/services/authApi';
import { useSession, useSessionActions } from '@/services/session';
import { SparkleIcon } from './icons';

/** Anchors into the landing page's own sections — kept to real, existing content only. */
const marketingLinks = [
  { href: '#top', label: 'Home' },
  { href: '#benefits', label: 'Features' },
  { href: '#workflow', label: 'How it works' },
  { href: '#faq', label: 'FAQ' },
];

/** Real app routes, shown instead of the marketing anchors once a session exists —
 *  identical to AppHeader's nav so the chrome is the same navbar everywhere. */
const appLinks = [
  { to: '/dashboard', label: 'Dashboard', icon: GridIcon },
  { to: '/profile', label: 'Profile', icon: UserIcon },
];

export function SiteHeader() {
  const [scrolled, setScrolled] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const { data: user } = useSession();
  const { clearSession } = useSessionActions();
  const location = useLocation();

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  const handleLogout = async () => {
    setMenuOpen(false);
    try {
      await logoutRequest();
    } catch {
      // Clear the local session regardless — the cookie is httpOnly and server-revoked either way.
    }
    // See the matching comment in AppHeader.tsx's handleLogout for why this is a hard
    // navigation rather than react-router's navigate().
    clearSession();
    window.location.assign('/');
  };

  return (
    <header
      className={`sticky top-0 z-50 border-b transition-colors duration-300 ${
        scrolled
          ? 'border-border bg-void/80 backdrop-blur-md'
          : 'border-transparent bg-transparent'
      }`}
    >
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-6">
        <a href="#top" className="flex items-center gap-2">
          <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-surface-2 text-ember-soft ring-1 ring-border-strong">
            <SparkleIcon className="h-4 w-4" />
          </span>
          <span className="text-base font-semibold tracking-tight text-ink">
            CareerForge <span className="text-gradient">AI</span>
          </span>
        </a>

        <nav aria-label="Primary" className="hidden items-center gap-1 md:flex">
          {user
            ? appLinks.map((link) => {
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
              })
            : marketingLinks.map((link) => (
                <a
                  key={link.href}
                  href={link.href}
                  className="rounded-lg px-3 py-2 text-sm text-ink-muted transition-colors hover:text-ink"
                >
                  {link.label}
                </a>
              ))}
        </nav>

        <div className="hidden items-center gap-4 md:flex">
          {user ? (
            <>
              <Link
                to="/generate"
                className="rounded-full bg-linear-to-r from-ember-soft to-rose px-4 py-2 text-sm font-semibold text-void transition-transform hover:-translate-y-0.5"
              >
                Generate
              </Link>
              <UserMenu user={user} onLogout={handleLogout} />
            </>
          ) : (
            <>
              <Link
                to="/login"
                className="rounded-lg px-3 py-2 text-sm text-ink-muted transition-colors hover:text-ink"
              >
                Log in
              </Link>
              <Link
                to="/generate"
                className="rounded-full bg-linear-to-r from-ember-soft to-rose px-4 py-2 text-sm font-semibold text-void transition-transform hover:-translate-y-0.5"
              >
                Get started
              </Link>
            </>
          )}
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
      </div>

      {menuOpen && (
        <nav aria-label="Primary" className="animate-menu-in border-t border-border bg-void md:hidden">
          <ul className="flex flex-col gap-1 px-6 py-4">
            {user
              ? appLinks.map((link) => {
                  const isActive =
                    location.pathname === link.to || location.pathname.startsWith(`${link.to}/`);
                  return (
                    <li key={link.to}>
                      <Link
                        to={link.to}
                        onClick={() => setMenuOpen(false)}
                        aria-current={isActive ? 'page' : undefined}
                        className={`block rounded-lg px-3 py-2 text-sm font-medium ${
                          isActive ? 'bg-surface-2 text-ink' : 'text-ink-muted hover:text-ink'
                        }`}
                      >
                        {link.label}
                      </Link>
                    </li>
                  );
                })
              : marketingLinks.map((link) => (
                  <li key={link.href}>
                    <a
                      href={link.href}
                      onClick={() => setMenuOpen(false)}
                      className="block rounded-lg px-3 py-2 text-sm text-ink-muted hover:text-ink"
                    >
                      {link.label}
                    </a>
                  </li>
                ))}
            {user ? (
              <>
                <li>
                  <Link
                    to="/generate"
                    onClick={() => setMenuOpen(false)}
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
              </>
            ) : (
              <>
                <li>
                  <Link
                    to="/login"
                    onClick={() => setMenuOpen(false)}
                    className="block rounded-lg px-3 py-2 text-sm text-ink-muted hover:text-ink"
                  >
                    Log in
                  </Link>
                </li>
                <li>
                  <Link
                    to="/generate"
                    onClick={() => setMenuOpen(false)}
                    className="mt-1 block rounded-full bg-linear-to-r from-ember-soft to-rose px-4 py-2 text-center text-sm font-semibold text-void"
                  >
                    Get started
                  </Link>
                </li>
              </>
            )}
          </ul>
        </nav>
      )}
    </header>
  );
}
