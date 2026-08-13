import { ErrorBanner } from '@/components/ui/ErrorBanner';

const ArrowIcon = ({ className }: { className?: string }) => (
  <svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth={2}
    strokeLinecap="round"
    strokeLinejoin="round"
    className={className}
    aria-hidden="true"
  >
    <path d="M5 12h13" />
    <path d="m13 6 6 6-6 6" />
  </svg>
);

/** Final, single bottom action card (redesign spec &sect;14) — copy and disabled/blocking reason
 *  are the only things that change per generation type; the CTA always calls the same existing
 *  generation handler the caller passes in (`onGenerate`), never a mock action. */
export function GenerationCta({
  heading,
  description,
  ctaLabel,
  onGenerate,
  disabled,
  disabledReason,
  loading,
  error,
}: {
  heading: string;
  description: string;
  ctaLabel: string;
  onGenerate: () => void;
  disabled?: boolean;
  disabledReason?: string;
  loading?: boolean;
  error?: unknown;
}) {
  return (
    <div className="relative overflow-hidden rounded-2xl bg-linear-to-r from-ember-soft to-rose p-6 sm:p-8">
      <div className="relative flex flex-col gap-5 sm:flex-row sm:items-center sm:justify-between">
        <div className="max-w-xl">
          <p className="flex items-center gap-2 text-lg font-semibold text-void">
            <span aria-hidden="true">✨</span> {heading}
          </p>
          <p className="mt-1.5 text-sm text-void/80">{description}</p>
          {disabled && disabledReason && <p className="mt-2 text-xs font-medium text-void/90">{disabledReason}</p>}
        </div>
        <button
          type="button"
          onClick={onGenerate}
          disabled={disabled || loading}
          className="group inline-flex shrink-0 items-center gap-3 rounded-full bg-void py-2 pl-6 pr-2 text-sm font-semibold text-ink shadow-lg shadow-black/25 transition-all hover:-translate-y-0.5 hover:shadow-xl hover:shadow-black/30 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ink/50 active:translate-y-0 disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:translate-y-0 disabled:hover:shadow-lg"
        >
          {loading && (
            <span className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-current border-t-transparent" aria-hidden="true" />
          )}
          <span className="py-1.5">{ctaLabel}</span>
          {!loading && (
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-ink text-void transition-transform duration-200 group-hover:translate-x-0.5 group-disabled:translate-x-0">
              <ArrowIcon className="h-4 w-4" />
            </span>
          )}
        </button>
      </div>
      {error !== undefined && error !== null && (
        <div className="relative mt-4 rounded-xl bg-void/90 p-3">
          <ErrorBanner error={error} />
        </div>
      )}
    </div>
  );
}
