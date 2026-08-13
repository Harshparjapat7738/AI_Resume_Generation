import { useEffect, useRef } from 'react';
import { Button } from '@/components/ui/Button';
import { DownloadIcon } from '@/features/dashboard/icons';

const AUTO_REDIRECT_SECONDS = 2.5;

/**
 * Full-screen overlay covering "Download Resume PDF" click → file actually saved → a brief
 * confirmation → back to the dashboard. Reuses the same celebrate-in/celebrate-ring "success
 * moment" language `ProfileCompletionModal` already established for the onboarding-complete
 * milestone, so a completed download reads as the same kind of moment rather than a new,
 * unfamiliar animation. This is a purely visual layer over `preparePdf`'s existing mutation
 * lifecycle in ResultPage — the real file download (the programmatic anchor click) still
 * happens exactly as before; this only reflects its two real states (in flight / succeeded).
 * Never shown on failure — ResultPage stops rendering this the moment the mutation errors, so
 * the existing "PDF unavailable" banner takes over instead of a fake success screen.
 */
export function DownloadingOverlay({
  stage,
  onGoToDashboard,
}: {
  stage: 'downloading' | 'done';
  onGoToDashboard: () => void;
}) {
  const dialogRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    // Only the "done" stage is dismissible/auto-advancing — there's nothing to skip to yet
    // while the file is still being rendered and saved.
    if (stage !== 'done') return;
    dialogRef.current?.focus();

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onGoToDashboard();
    };
    document.addEventListener('keydown', onKeyDown);

    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    // A convenience, not the only path — the button below is always right there. Skipped
    // under reduced-motion so the screen doesn't just move on someone who asked for less
    // automatic movement.
    const timer = prefersReducedMotion ? null : setTimeout(onGoToDashboard, AUTO_REDIRECT_SECONDS * 1000);

    return () => {
      document.removeEventListener('keydown', onKeyDown);
      if (timer) clearTimeout(timer);
    };
  }, [stage, onGoToDashboard]);

  return (
    <div
      className="animate-toast-in fixed inset-0 z-50 flex items-center justify-center bg-void/80 px-4 backdrop-blur-sm"
      onClick={stage === 'done' ? onGoToDashboard : undefined}
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="download-status-title"
        tabIndex={-1}
        onClick={(event) => event.stopPropagation()}
        className="animate-toast-in w-full max-w-sm rounded-2xl border border-border bg-surface p-8 text-center shadow-2xl shadow-black/50 focus:outline-none"
      >
        {stage === 'downloading' ? (
          <span className="relative mx-auto flex h-16 w-16 items-center justify-center">
            <span
              className="absolute inset-0 animate-spin rounded-full border-2 border-ember-soft border-t-transparent"
              aria-hidden="true"
            />
            <DownloadIcon className="h-7 w-7 text-ink-muted" />
          </span>
        ) : (
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
        )}

        <h2 id="download-status-title" className="mt-5 text-lg font-semibold tracking-tight text-ink">
          {stage === 'downloading' ? 'Downloading your resume…' : 'Download complete'}
        </h2>
        <p className="mt-2 text-sm text-ink-muted">
          {stage === 'downloading'
            ? 'Rendering the PDF from your template and saving it to your device.'
            : 'Your resume PDF has been saved. Taking you back to your dashboard…'}
        </p>

        {stage === 'done' && (
          <Button type="button" onClick={onGoToDashboard} className="mt-6 w-full">
            Go to Dashboard now
          </Button>
        )}
      </div>
    </div>
  );
}
