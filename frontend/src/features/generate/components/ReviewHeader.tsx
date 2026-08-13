import { useNavigate } from 'react-router-dom';
import { UserMenu } from '@/components/layout/UserMenu';
import { useLogout } from '@/features/dashboard/hooks';
import { useSession } from '@/services/session';

const BackIcon = ({ className }: { className?: string }) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.75} strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden="true">
    <path d="m11 5-6.5 7L11 19" />
    <path d="M5 12h15" />
  </svg>
);

const SaveIcon = ({ className }: { className?: string }) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.75} strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden="true">
    <path d="M5.5 4h10l3 3v13h-13z" />
    <path d="M8.5 4v5h7V4M8 14.5h8v5H8z" />
  </svg>
);

/** Compact top bar for the generation workflow (redesign spec &sect;4) — deliberately not the
 *  full marketing AppHeader (too tall for a working screen) and not a second nav implementation:
 *  DashboardSidebar right beside it already owns navigation, this only owns page-level actions. */
export function ReviewHeader({
  title,
  backLabel = 'Back',
  backTo,
  onBack,
  showSaveDraft = true,
}: {
  title: string;
  /** e.g. "Back to output" on the Job Description step — purely a label, doesn't change where
   *  `backTo`/`onBack` navigate. */
  backLabel?: string;
  backTo?: string;
  onBack?: () => void;
  /** Off for steps that haven't persisted anything yet to truthfully "save" (e.g. the Job
   *  Description step before it's been submitted) — on by default for steps where it's true. */
  showSaveDraft?: boolean;
}) {
  const navigate = useNavigate();
  const { data: user } = useSession();
  const handleLogout = useLogout();

  const goBack = () => {
    if (onBack) return onBack();
    if (backTo) return navigate(backTo);
    navigate(-1);
  };

  return (
    // pl-16 (lg:pl-10, matching <main>'s own left padding) reserves room for
    // DashboardSidebar's fixed mobile/tablet hamburger trigger (top-4 left-4, lg:hidden) so it
    // never sits on top of the Back button below it.
    <header className="sticky top-0 z-20 flex h-16 shrink-0 items-center justify-between gap-4 border-b border-border bg-void/95 pl-16 pr-5 backdrop-blur sm:pr-7 lg:pl-10 lg:pr-10">
      <div className="flex min-w-0 items-center gap-3">
        <button
          type="button"
          onClick={goBack}
          className="flex h-9 items-center gap-1.5 rounded-full border border-border-strong px-3 text-sm text-ink-muted transition-colors hover:border-ink-muted hover:text-ink"
        >
          <BackIcon className="h-4 w-4" />
          {backLabel}
        </button>
        <h1 className="truncate text-sm font-semibold text-ink sm:text-base">{title}</h1>
      </div>

      {user && (
        <div className="flex shrink-0 items-center gap-3">
          {showSaveDraft && (
            <button
              type="button"
              onClick={() => navigate('/dashboard')}
              title="Your job description is already saved — come back to it anytime."
              className="hidden h-9 items-center gap-1.5 rounded-full border border-border-strong px-3.5 text-sm text-ink-muted transition-colors hover:border-ink-muted hover:text-ink sm:flex"
            >
              <SaveIcon className="h-4 w-4" />
              Save draft
            </button>
          )}
          <UserMenu user={user} onLogout={handleLogout} />
        </div>
      )}
    </header>
  );
}
