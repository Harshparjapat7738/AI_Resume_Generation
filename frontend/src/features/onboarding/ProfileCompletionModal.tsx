import { useEffect, useRef } from 'react';
import { Button } from '@/components/ui/Button';

const AUTO_REDIRECT_SECONDS = 6;

/**
 * The reward for reaching 100% profile completion — shown once, from OnboardingPage's
 * "Finish profile" action, never on a partial/failed save (see the guard around where this
 * is rendered). Backdrop click, Escape and the button all do the same thing: there's nothing
 * to "cancel" back to here, the only place this leads is the dashboard.
 */
export function ProfileCompletionModal({
  name,
  onGoToDashboard,
}: {
  name: string;
  onGoToDashboard: () => void;
}) {
  const dialogRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    dialogRef.current?.focus();

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onGoToDashboard();
    };
    document.addEventListener('keydown', onKeyDown);

    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    // A subtle convenience, not the primary path — the button is always right there. Skipped
    // entirely under reduced-motion so the moment doesn't just vanish on someone who's asked
    // for less automatic movement on screen.
    const timer = prefersReducedMotion ? null : setTimeout(onGoToDashboard, AUTO_REDIRECT_SECONDS * 1000);

    return () => {
      document.removeEventListener('keydown', onKeyDown);
      if (timer) clearTimeout(timer);
    };
  }, [onGoToDashboard]);

  return (
    <div
      className="animate-toast-in fixed inset-0 z-50 flex items-center justify-center bg-void/80 px-4 backdrop-blur-sm"
      onClick={onGoToDashboard}
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="profile-complete-title"
        tabIndex={-1}
        onClick={(event) => event.stopPropagation()}
        className="animate-toast-in w-full max-w-md rounded-2xl border border-border bg-surface p-8 text-center shadow-2xl shadow-black/50 focus:outline-none"
      >
        <span className="relative mx-auto flex h-16 w-16 items-center justify-center">
          <span
            className="animate-celebrate-ring absolute inset-0 rounded-full bg-linear-to-br from-ember-soft to-rose"
            aria-hidden="true"
          />
          <span
            className="animate-celebrate-in relative flex h-16 w-16 items-center justify-center rounded-full bg-linear-to-br from-ember-soft to-rose text-void"
            aria-hidden="true"
          >
            <svg viewBox="0 0 20 20" fill="currentColor" className="h-8 w-8">
              <path
                fillRule="evenodd"
                d="M16.7 5.3a1 1 0 0 1 0 1.4l-7.5 7.5a1 1 0 0 1-1.4 0l-3.5-3.5a1 1 0 1 1 1.4-1.4l2.8 2.8 6.8-6.8a1 1 0 0 1 1.4 0Z"
                clipRule="evenodd"
              />
            </svg>
          </span>
        </span>

        <h2 id="profile-complete-title" className="mt-5 text-xl font-semibold tracking-tight text-ink">
          Congratulations, {name}!
        </h2>
        <p className="mt-1 text-sm font-medium text-ink-muted">Your profile is complete.</p>
        <p className="mt-3 text-sm text-ink-muted">
          Every generated resume, cover letter and email is grounded only in what you've
          added — your profile is now ready to power all three.
        </p>

        <div className="mt-6 flex items-center justify-center gap-3">
          <div className="h-1.5 w-36 overflow-hidden rounded-full bg-surface-2">
            <div className="h-full w-full rounded-full bg-linear-to-r from-ember-soft to-rose" />
          </div>
          <span className="text-sm font-semibold text-ink">100%</span>
        </div>

        <Button type="button" onClick={onGoToDashboard} className="mt-7 w-full">
          Go to Dashboard
        </Button>
      </div>
    </div>
  );
}
