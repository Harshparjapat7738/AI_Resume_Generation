import { jdFitChecks } from '../content';
import { CheckIcon, GaugeIcon, ShieldCheckIcon, SparkleIcon, TrendUpIcon } from './icons';

const overall = Math.round(
  jdFitChecks.reduce((sum, check) => sum + check.score, 0) / jdFitChecks.length,
);

/**
 * The hero's visual centrepiece (redesign brief §2, "right side") — a real-product-shaped
 * mockup (JD-fit score + weighted breakdown, exactly the numbers `jdFitChecks` describes,
 * see content.ts) rather than an illustrated mascot: this brand has never had one, and a
 * screenshot-style card reads as more credible for a job-application tool anyway. Small
 * floating badge cards echo the reference composition's decorative corner elements.
 * `compact` drops the floating badges and shrinks padding for reuse in the final CTA.
 */
export function HeroIllustration({ compact = false }: { compact?: boolean }) {
  return (
    <div className="relative">
      <div
        className={`animate-float rounded-3xl border border-border bg-surface/90 shadow-2xl shadow-brand/10 backdrop-blur ${
          compact ? 'p-5' : 'p-6 sm:p-7'
        }`}
      >
        <div className="flex items-center justify-between">
          <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">
            Job description match
          </p>
          <span className="rounded-full bg-mint/10 px-2.5 py-1 text-xs font-semibold text-mint">
            STRONG
          </span>
        </div>

        <div className="mt-5 flex items-center gap-5">
          <div className="relative flex h-20 w-20 shrink-0 items-center justify-center">
            <svg viewBox="0 0 100 100" className="h-20 w-20 -rotate-90">
              <circle cx="50" cy="50" r="42" fill="none" stroke="var(--color-surface-2)" strokeWidth="10" />
              <circle
                cx="50"
                cy="50"
                r="42"
                fill="none"
                stroke="url(#hero-gauge-gradient)"
                strokeWidth="10"
                strokeLinecap="round"
                strokeDasharray={`${(overall / 100) * 263.9} 263.9`}
              />
              <defs>
                <linearGradient id="hero-gauge-gradient" x1="0" y1="0" x2="1" y2="0">
                  <stop offset="0%" stopColor="var(--color-brand)" />
                  <stop offset="100%" stopColor="var(--color-brand-2)" />
                </linearGradient>
              </defs>
            </svg>
            <span className="absolute text-xl font-semibold text-ink">{overall}</span>
          </div>
          <p className="text-sm leading-relaxed text-ink-muted">
            Coverage, keywords, seniority and recency — all explained against this JD's real
            requirements.
          </p>
        </div>

        <div className="mt-6 space-y-3.5">
          {jdFitChecks.map((check) => (
            <div key={check.label}>
              <div className="flex items-center justify-between text-xs">
                <span className="text-ink-muted">{check.label}</span>
                <span className="font-medium text-ink">{check.score}</span>
              </div>
              <div className="mt-1.5 h-1.5 w-full overflow-hidden rounded-full bg-surface-2">
                <div
                  className="h-full rounded-full bg-linear-to-r from-brand to-brand-2"
                  style={{ width: `${check.score}%` }}
                />
              </div>
            </div>
          ))}
        </div>

        <div className="mt-6 flex items-center gap-2 rounded-xl border border-border bg-surface-2 px-3 py-2.5 text-xs text-ink-muted">
          <CheckIcon className="h-3.5 w-3.5 shrink-0 text-mint" />
          Every line traces to an evidence ID in your profile.
        </div>
      </div>

      {!compact && (
        <>
          <div
            className="animate-float-delayed absolute -right-5 -top-6 hidden items-center gap-2 rounded-2xl border border-border bg-surface px-3.5 py-3 shadow-xl shadow-black/5 sm:flex"
            aria-hidden="true"
          >
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-mint/10 text-mint">
              <TrendUpIcon className="h-4 w-4" />
            </span>
            <div>
              <p className="text-[11px] font-semibold text-ink">+18 pts</p>
              <p className="text-[10px] text-ink-faint">after tailoring</p>
            </div>
          </div>

          <div
            className="animate-float absolute -bottom-5 -left-6 hidden h-14 w-14 items-center justify-center rounded-2xl border border-border bg-surface shadow-xl shadow-black/5 sm:flex"
            aria-hidden="true"
          >
            <ShieldCheckIcon className="h-6 w-6 text-brand" />
          </div>

          <div
            className="animate-float-delayed absolute -bottom-8 right-8 hidden h-11 w-11 items-center justify-center rounded-full border border-border bg-linear-to-br from-brand to-brand-2 shadow-xl shadow-brand/20 sm:flex"
            aria-hidden="true"
          >
            <SparkleIcon className="h-5 w-5 text-void" />
          </div>

          <div
            className="animate-float absolute left-1/2 top-1/2 -z-10 h-72 w-72 -translate-x-1/2 -translate-y-1/2 rounded-full bg-brand/15 blur-3xl"
            aria-hidden="true"
          />
        </>
      )}

      {compact && (
        <div
          aria-hidden="true"
          className="absolute -bottom-6 -right-6 -z-10 h-40 w-40 rounded-full bg-brand/20 blur-3xl"
        />
      )}
    </div>
  );
}

/** Small "score dial" badge reused a couple of places (e.g. floating over the workspace
 *  illustration) — kept here so it shares the gauge-icon language rather than a second
 *  hand-rolled circle. */
export function ScoreBadge({ className = '' }: { className?: string }) {
  return (
    <div
      className={`flex items-center gap-2 rounded-full border border-border bg-surface px-3 py-2 shadow-lg shadow-black/5 ${className}`}
    >
      <span className="flex h-6 w-6 items-center justify-center rounded-full bg-brand/10 text-brand">
        <GaugeIcon className="h-3.5 w-3.5" />
      </span>
      <span className="text-xs font-semibold text-ink">Grounded</span>
    </div>
  );
}
