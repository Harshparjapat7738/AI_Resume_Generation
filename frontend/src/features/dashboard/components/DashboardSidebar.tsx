import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Link, useLocation } from 'react-router-dom';
import {
  BarChartIcon,
  DocumentIcon,
  GearIcon,
  GridIcon,
  HomeIcon,
  LayersIcon,
  MailIcon,
  MenuIcon,
  SendIcon,
  SparkleIcon,
  UserIcon,
  XIcon,
} from '../icons';

type IconComponent = (props: { className?: string }) => ReactNode;

type NavItem =
  | { label: string; icon: IconComponent; kind: 'link'; to: string; isActive: (pathname: string, search: string) => boolean }
  | { label: string; icon: IconComponent; kind: 'soon' };

// Every enabled item is a real dedicated page (routes/router.tsx) — Applications, Resumes,
// Cover Letters and Emails each get their own full-dataset page instead of pointing back at a
// Dashboard anchor/filter, and Templates/Analytics are now real pages too. Settings has no
// backend at all (no settings-service, no settings fields anywhere in the API surface), so —
// same principle as before — it's honestly marked "Soon" rather than linked to a page that
// isn't there.
const NAV_ITEMS: NavItem[] = [
  { label: 'Dashboard', icon: HomeIcon, kind: 'link', to: '/dashboard', isActive: (p) => p === '/dashboard' },
  { label: 'Applications', icon: LayersIcon, kind: 'link', to: '/applications', isActive: (p) => p === '/applications' },
  { label: 'Resumes', icon: DocumentIcon, kind: 'link', to: '/resumes', isActive: (p) => p === '/resumes' },
  { label: 'Cover Letters', icon: SendIcon, kind: 'link', to: '/cover-letters', isActive: (p) => p === '/cover-letters' },
  { label: 'Emails', icon: MailIcon, kind: 'link', to: '/emails', isActive: (p) => p === '/emails' },
  { label: 'Templates', icon: GridIcon, kind: 'link', to: '/templates', isActive: (p) => p === '/templates' },
  { label: 'Analytics', icon: BarChartIcon, kind: 'link', to: '/analytics', isActive: (p) => p === '/analytics' },
  { label: 'Profile', icon: UserIcon, kind: 'link', to: '/profile', isActive: (p) => p === '/profile' },
  { label: 'Settings', icon: GearIcon, kind: 'soon' },
];

function NavList({ onNavigate }: { onNavigate: () => void }) {
  const location = useLocation();
  return (
    <ul className="space-y-1">
      {NAV_ITEMS.map((item) => {
        const Icon = item.icon;
        if (item.kind === 'soon') {
          return (
            <li key={item.label}>
              <span className="flex cursor-not-allowed items-center justify-between rounded-xl px-3 py-2.5 text-sm text-ink-faint/70">
                <span className="flex items-center gap-3">
                  <Icon className="h-[18px] w-[18px]" />
                  {item.label}
                </span>
                <span className="rounded-full border border-border px-1.5 py-0.5 text-[10px]">Soon</span>
              </span>
            </li>
          );
        }
        const active = item.isActive(location.pathname, location.search);
        return (
          <li key={item.label}>
            <Link
              to={item.to}
              onClick={onNavigate}
              aria-current={active ? 'page' : undefined}
              className={`flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm transition-colors ${
                active
                  ? 'bg-surface-2 font-medium text-ink before:mr-0'
                  : 'text-ink-muted hover:bg-surface-2/60 hover:text-ink'
              }`}
            >
              <Icon className="h-[18px] w-[18px]" />
              {item.label}
            </Link>
          </li>
        );
      })}
    </ul>
  );
}

/** Persistent product sidebar for the dashboard "app shell" — distinct from AppHeader (the
 *  simple top bar every other authenticated page uses), matching the reference design's
 *  sidebar-first layout. Desktop: a fixed-width sticky rail. Mobile/tablet: collapses behind
 *  its own hamburger trigger into an off-canvas drawer — self-contained, so DashboardPage
 *  doesn't need to own any of this state. */
export function DashboardSidebar({ userName, userEmail }: { userName: string; userEmail: string }) {
  const [open, setOpen] = useState(false);

  // Route changes (including clicking a nav item) close the mobile drawer.
  const location = useLocation();
  useEffect(() => {
    setOpen(false);
  }, [location.pathname, location.search]);

  useEffect(() => {
    if (!open) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false);
    };
    document.addEventListener('keydown', onKeyDown);
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = '';
    };
  }, [open]);

  const brand = (
    <Link to="/" className="flex items-center gap-2 px-1">
      <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-surface-2 text-ember-soft ring-1 ring-border-strong">
        <SparkleIcon className="h-4 w-4" />
      </span>
      <span className="text-base font-semibold tracking-tight text-ink">
        CareerForge <span className="text-gradient">AI</span>
      </span>
    </Link>
  );

  const accountCard = (
    <div className="rounded-xl border border-border bg-surface-2/50 px-3 py-3">
      <div className="flex items-center gap-2.5">
        <span
          className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-linear-to-br from-ember-soft to-rose text-xs font-semibold text-void"
          aria-hidden="true"
        >
          {userName.slice(0, 2).toUpperCase()}
        </span>
        <div className="min-w-0">
          <p className="truncate text-sm font-medium text-ink">{userName}</p>
          <p className="truncate text-xs text-ink-faint">{userEmail}</p>
        </div>
      </div>
    </div>
  );

  return (
    <>
      {/* Desktop: sticky rail, part of the page's normal flow. */}
      <aside className="hidden shrink-0 border-r border-border bg-surface lg:sticky lg:top-0 lg:flex lg:h-screen lg:w-64 lg:flex-col lg:justify-between lg:px-4 lg:py-6">
        <div>
          {brand}
          <nav aria-label="Dashboard" className="mt-8">
            <NavList onNavigate={() => {}} />
          </nav>
        </div>
        {accountCard}
      </aside>

      {/* Mobile / tablet: hamburger trigger + off-canvas drawer. */}
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-label="Open dashboard menu"
        aria-expanded={open}
        className="fixed left-4 top-4 z-30 flex h-10 w-10 items-center justify-center rounded-xl border border-border bg-surface text-ink shadow-lg shadow-black/30 lg:hidden"
      >
        <MenuIcon className="h-5 w-5" />
      </button>

      {open && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div className="absolute inset-0 bg-void/70 backdrop-blur-sm animate-toast-in" onClick={() => setOpen(false)} />
          <aside className="animate-menu-in relative flex h-full w-72 max-w-[80vw] flex-col justify-between border-r border-border bg-surface px-4 py-6 shadow-2xl">
            <div>
              <div className="flex items-center justify-between">
                {brand}
                <button
                  type="button"
                  onClick={() => setOpen(false)}
                  aria-label="Close dashboard menu"
                  className="flex h-8 w-8 items-center justify-center rounded-lg text-ink-muted hover:text-ink"
                >
                  <XIcon className="h-5 w-5" />
                </button>
              </div>
              <nav aria-label="Dashboard" className="mt-8">
                <NavList onNavigate={() => setOpen(false)} />
              </nav>
            </div>
            {accountCard}
          </aside>
        </div>
      )}
    </>
  );
}
