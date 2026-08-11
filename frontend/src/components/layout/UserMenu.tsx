import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import type { MeResponse } from '@/services/authApi';
import { ChevronDownIcon, LogOutIcon, UserIcon } from './icons';

function initialsFor(user: Pick<MeResponse, 'displayName' | 'email'>): string {
  const source = user.displayName?.trim() || user.email;
  const parts = source.split(/\s+/).filter(Boolean);
  if (parts.length >= 2) {
    return `${parts[0]![0]}${parts[1]![0]}`.toUpperCase();
  }
  return source.slice(0, 2).toUpperCase();
}

/**
 * Avatar dropdown shared by every authenticated header (SiteHeader when logged in,
 * AppHeader everywhere else) — one place for the logout affordance so auth logic
 * never gets duplicated between them.
 */
export function UserMenu({ user, onLogout }: { user: MeResponse; onLogout: () => void }) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const firstItemRef = useRef<HTMLAnchorElement>(null);

  useEffect(() => {
    if (!open) return;
    firstItemRef.current?.focus();

    const onPointerDown = (event: PointerEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false);
        rootRef.current?.querySelector('button')?.focus();
      }
    };
    document.addEventListener('pointerdown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label="Account menu"
        className="flex items-center gap-2 rounded-full border border-transparent py-1 pl-1 pr-2 text-sm transition-colors hover:border-border focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember-soft"
      >
        <span
          className="flex h-8 w-8 items-center justify-center rounded-full bg-linear-to-br from-ember-soft to-rose text-xs font-semibold text-void"
          aria-hidden="true"
        >
          {initialsFor(user)}
        </span>
        <span className="hidden max-w-[10rem] truncate text-ink-muted md:inline">
          {user.displayName || user.email}
        </span>
        <ChevronDownIcon
          className={`h-3.5 w-3.5 text-ink-faint transition-transform ${open ? 'rotate-180' : ''}`}
        />
      </button>

      {open && (
        <div
          role="menu"
          aria-label="Account menu"
          className="absolute right-0 top-full z-50 mt-2 w-56 origin-top-right rounded-xl border border-border bg-surface p-1.5 shadow-xl shadow-black/40"
        >
          <div className="border-b border-border px-3 py-2">
            <p className="truncate text-sm font-medium text-ink">{user.displayName || 'Your account'}</p>
            <p className="truncate text-xs text-ink-faint">{user.email}</p>
          </div>
          <Link
            ref={firstItemRef}
            to="/profile"
            role="menuitem"
            onClick={() => setOpen(false)}
            className="mt-1 flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm text-ink-muted transition-colors hover:bg-surface-2 hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember-soft"
          >
            <UserIcon className="h-4 w-4" />
            Profile
          </Link>
          <button
            type="button"
            role="menuitem"
            onClick={() => {
              setOpen(false);
              onLogout();
            }}
            className="flex w-full items-center gap-2.5 rounded-lg px-3 py-2 text-left text-sm text-ink-muted transition-colors hover:bg-surface-2 hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember-soft"
          >
            <LogOutIcon className="h-4 w-4" />
            Log out
          </button>
        </div>
      )}
    </div>
  );
}
