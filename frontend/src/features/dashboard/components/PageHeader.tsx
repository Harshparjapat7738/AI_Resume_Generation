import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { UserMenu } from '@/components/layout/UserMenu';
import type { MeResponse } from '@/services/authApi';
import { BellIcon, ChevronLeftIcon } from '../icons';

/** Shared header for every dedicated data page (Applications/Resumes/Cover Letters/Emails/
 *  Templates/Analytics) — title, description and a page-specific action on the left/middle,
 *  the same notification bell + account menu Dashboard's own header uses on the right, plus a
 *  lightweight "Back to Dashboard" breadcrumb since the sidebar's active item already covers
 *  primary navigation and a full breadcrumb trail would just repeat it. */
export function PageHeader({
  title,
  description,
  action,
  user,
  onLogout,
}: {
  title: string;
  description: string;
  action?: ReactNode;
  user: MeResponse;
  onLogout: () => void;
}) {
  return (
    <div>
      <Link
        to="/dashboard"
        className="inline-flex items-center gap-1 text-xs font-medium text-ink-faint transition-colors hover:text-ink"
      >
        <ChevronLeftIcon className="h-3.5 w-3.5" />
        Dashboard
      </Link>
      <div className="mt-2 flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-ink">{title}</h1>
          <p className="mt-1 text-sm text-ink-muted">{description}</p>
        </div>
        <div className="flex items-center gap-3">
          {action}
          <button
            type="button"
            aria-label="Notifications"
            className="flex h-10 w-10 items-center justify-center rounded-full border border-border text-ink-muted transition-colors hover:border-border-strong hover:text-ink"
          >
            <BellIcon className="h-[18px] w-[18px]" />
          </button>
          <UserMenu user={user} onLogout={onLogout} />
        </div>
      </div>
    </div>
  );
}
