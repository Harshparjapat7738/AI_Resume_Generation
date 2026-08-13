import { Link } from 'react-router-dom';
import { UserMenu } from '@/components/layout/UserMenu';
import { Button } from '@/components/ui/Button';
import { BellIcon, ChevronLeftIcon } from '@/features/dashboard/icons';
import type { MeResponse } from '@/services/authApi';

/** The compact, generic top bar every result page (resume, cover letter, email, "Generate
 *  All") shares — back-link, "Generate New", notification bell, account menu. Deliberately
 *  not `PageHeader` (every other dedicated page's shared header): that component always
 *  reserves a title/description row, which these pages don't want since their real title
 *  lives in each page's own wider hero card instead. Built from the exact same pieces (icons,
 *  `UserMenu`) `PageHeader` itself uses, so it looks identical in every way that isn't the
 *  title row. Originally local to `ResultPage`, extracted here once the other result pages
 *  needed the same bar for UI parity with it. */
export function ResultTopBar({ user, onLogout }: { user: MeResponse; onLogout: () => void }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-4">
      <Link
        to="/applications"
        className="inline-flex items-center gap-1 text-xs font-medium text-ink-faint transition-colors hover:text-ink"
      >
        <ChevronLeftIcon className="h-3.5 w-3.5" />
        Back to results
      </Link>
      <div className="flex items-center gap-3">
        <Link to="/generate">
          <Button className="!px-4 !py-2.5 !text-sm">Generate New</Button>
        </Link>
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
  );
}
