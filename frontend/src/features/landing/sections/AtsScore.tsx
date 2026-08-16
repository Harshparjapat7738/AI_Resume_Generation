import { Reveal } from '../components/Reveal';
import { CheckIcon, GaugeIcon, TrendUpIcon } from '../components/icons';
import { atsChecks } from '../content';

const overall = Math.round(
  atsChecks.reduce((sum, check) => sum + check.score, 0) / atsChecks.length,
);

/**
 * "See your ATS score, explained" — the redesign brief's "For Companies" slot (§7),
 * repurposed: this product has no employer-facing screening feature, so a fabricated one
 * would be dishonest filler. What it does have is a real, deterministic scoring engine —
 * shown here as the dashboard-style mockup the brief asks for, built from the actual
 * `atsChecks` data (see content.ts).
 */
export function AtsScore() {
  return (
    <section id="ats-score" className="scroll-mt-20 border-t border-border bg-cream py-20 sm:py-24">
      <div className="mx-auto max-w-6xl px-6">
        <div className="grid gap-14 lg:grid-cols-[1.05fr_0.95fr] lg:items-center">
          <Reveal>
            <div className="overflow-hidden rounded-2xl border border-border bg-surface shadow-2xl shadow-black/5">
              <div className="flex items-center gap-1.5 border-b border-border px-4 py-3">
                <span className="h-2.5 w-2.5 rounded-full bg-rose/60" />
                <span className="h-2.5 w-2.5 rounded-full bg-ember-soft/60" />
                <span className="h-2.5 w-2.5 rounded-full bg-mint/60" />
                <span className="ml-3 text-xs text-ink-faint">ATS report — sample</span>
              </div>

              <div className="p-6 sm:p-7">
                <div className="flex items-center gap-5">
                  <div className="relative flex h-20 w-20 shrink-0 items-center justify-center">
                    <svg viewBox="0 0 100 100" className="h-20 w-20 -rotate-90">
                      <circle cx="50" cy="50" r="42" fill="none" stroke="var(--color-surface-2)" strokeWidth="10" />
                      <circle
                        cx="50"
                        cy="50"
                        r="42"
                        fill="none"
                        stroke="url(#ats-gauge-gradient)"
                        strokeWidth="10"
                        strokeLinecap="round"
                        strokeDasharray={`${(overall / 100) * 263.9} 263.9`}
                      />
                      <defs>
                        <linearGradient id="ats-gauge-gradient" x1="0" y1="0" x2="1" y2="0">
                          <stop offset="0%" stopColor="var(--color-brand)" />
                          <stop offset="100%" stopColor="var(--color-brand-2)" />
                        </linearGradient>
                      </defs>
                    </svg>
                    <span className="absolute text-xl font-semibold text-ink">{overall}</span>
                  </div>
                  <div>
                    <div className="flex items-center gap-2">
                      <p className="text-sm font-medium text-ink">Overall ATS score</p>
                      <span className="rounded-full bg-mint/10 px-2 py-0.5 text-[11px] font-semibold text-mint">
                        STRONG
                      </span>
                    </div>
                    <p className="mt-1 text-xs text-ink-faint">Weighted average of seven checks</p>
                  </div>
                </div>

                <ul className="mt-7 space-y-4">
                  {atsChecks.map((check) => (
                    <li key={check.label}>
                      <div className="flex items-center justify-between text-xs">
                        <span className="text-ink-muted">{check.label}</span>
                        <span className="font-medium text-ink">{check.score}/100</span>
                      </div>
                      <div className="mt-1.5 h-1.5 w-full overflow-hidden rounded-full bg-surface-2">
                        <div
                          className="h-full rounded-full bg-linear-to-r from-brand to-brand-2"
                          style={{ width: `${check.score}%` }}
                        />
                      </div>
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          </Reveal>

          <Reveal delay={0.1}>
            <p className="text-sm font-semibold uppercase tracking-wide text-brand">ATS score</p>
            <h2 className="mt-3 text-3xl font-semibold tracking-tight text-ink sm:text-4xl">
              See your ATS score, explained
            </h2>
            <p className="mt-4 text-lg leading-relaxed text-ink-muted">
              Seven weighted checks run deterministically against your resume — parse
              safety, structure, keyword match and more. The language model is never asked
              for a number; it never even sees this part of the pipeline.
            </p>

            <ul className="mt-6 space-y-3">
              {[
                { icon: GaugeIcon, label: 'Computed in Java, not guessed by a language model' },
                { icon: CheckIcon, label: 'Explainable down to the sub-check, not just a total' },
                { icon: TrendUpIcon, label: 'Recomputed the moment your profile or resume changes' },
              ].map((item) => (
                <li key={item.label} className="flex items-center gap-3 text-sm text-ink-muted">
                  <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-surface text-brand ring-1 ring-border">
                    <item.icon className="h-4 w-4" />
                  </span>
                  {item.label}
                </li>
              ))}
            </ul>

            <a
              href="#faq"
              className="mt-7 inline-flex items-center gap-2 rounded-full bg-linear-to-r from-brand to-brand-2 px-5 py-2.5 text-sm font-semibold text-void transition-transform hover:-translate-y-0.5"
            >
              Learn how it's scored →
            </a>
          </Reveal>
        </div>
      </div>
    </section>
  );
}
