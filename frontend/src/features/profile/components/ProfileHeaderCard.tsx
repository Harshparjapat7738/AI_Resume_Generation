import { Link } from 'react-router-dom';
import type { ProfileCompletion, ProfileResponse } from '@/services/profileApi';
import { PROFILE_SECTIONS } from '../sections';

function initialsFor(fullName: string | null, email: string | null): string {
  const source = fullName?.trim() || email?.trim() || '';
  if (!source) return '?';
  const parts = source.split(/\s+/).filter(Boolean);
  if (parts.length >= 2) return `${parts[0]![0]}${parts[1]![0]}`.toUpperCase();
  return source.slice(0, 2).toUpperCase();
}

/**
 * Full-bleed hero directly under the navbar — deliberately NOT inside the page's centered
 * max-width container (ProfilePage.tsx renders this outside that wrapper), so its gradient
 * background spans the whole viewport the way the header above it does.
 */
export function ProfileHeaderCard({
  profile,
  completion,
  onEditProfile,
}: {
  profile: ProfileResponse;
  completion: ProfileCompletion;
  /** Jumps the single-section view to Personal Information — see ProfilePage.tsx. There's
   *  nothing to scroll to any more (only one section is ever mounted at a time), so this is a
   *  callback into that state instead of the old scrollToSection anchor jump. */
  onEditProfile: () => void;
}) {
  const info = profile.personalInformation;
  const completedCount = PROFILE_SECTIONS.filter((s) => completion.sections[s.key]).length;

  return (
    <div className="relative overflow-hidden border-b border-border bg-forge-glow bg-surface">
      <div className="mx-auto max-w-6xl px-4 py-8 sm:px-6 lg:py-10">
        <div className="flex flex-col gap-6 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex min-w-0 items-center gap-4">
            <span
              className="flex h-16 w-16 shrink-0 items-center justify-center rounded-full bg-linear-to-br from-ember-soft to-rose text-xl font-semibold text-void"
              aria-hidden="true"
            >
              {initialsFor(info.fullName, info.email)}
            </span>
            <div className="min-w-0">
              <h1 className="truncate text-2xl font-semibold tracking-tight text-ink">
                {info.fullName || 'Add your name'}
              </h1>
              <p className="mt-0.5 truncate text-sm text-ink-muted">
                {info.headline || 'Add a professional headline'}
              </p>
              {info.email && <p className="mt-0.5 truncate text-sm text-ink-faint">{info.email}</p>}
            </div>
          </div>

          <div className="w-full shrink-0 lg:w-72">
            <div className="flex items-baseline justify-between gap-3">
              <p className="text-xs font-semibold uppercase tracking-wide text-ink-faint">Profile completion</p>
              <span className="text-sm font-semibold text-ink">{completion.percentage}%</span>
            </div>
            <div className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-surface-2">
              <div
                className="h-full rounded-full bg-linear-to-r from-ember-soft to-rose transition-all"
                style={{ width: `${completion.percentage}%` }}
              />
            </div>
            <div className="mt-3 flex items-center justify-between gap-3">
              <p className="text-xs text-ink-faint">
                {completedCount} of {PROFILE_SECTIONS.length} sections completed
              </p>
              <div className="flex shrink-0 items-center gap-3">
                <Link
                  to="/profile/templates"
                  className="text-xs font-medium text-ink-muted underline underline-offset-2 transition-colors hover:text-ink"
                >
                  My Templates
                </Link>
                <button
                  type="button"
                  onClick={onEditProfile}
                  className="text-xs font-medium text-ink-muted underline underline-offset-2 transition-colors hover:text-ink"
                >
                  Edit profile
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
