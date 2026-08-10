import { Reveal } from '../components/Reveal';
import { atsChecks } from '../content';

export function AtsScore() {
  const overall = Math.round(
    atsChecks.reduce((sum, check) => sum + check.score, 0) / atsChecks.length,
  );

  return (
    <section id="ats-score" className="scroll-mt-20 border-t border-border bg-void py-24">
      <div className="mx-auto max-w-6xl px-6">
        <div className="grid gap-14 lg:grid-cols-[0.9fr_1.1fr] lg:items-center">
          <Reveal>
            <p className="text-sm font-medium uppercase tracking-wide text-ember-soft">
              ATS score
            </p>
            <h2 className="mt-3 text-3xl font-semibold tracking-tight text-ink sm:text-4xl">
              A score computed in Java, not guessed by a language model.
            </h2>
            <p className="mt-4 text-lg leading-relaxed text-ink-muted">
              Ten weighted checks run deterministically against your resume and the job
              description — parse safety, structure, keyword match, seniority alignment and
              more. The language model is never asked for a number; it can't be, because it
              never sees this part of the pipeline.
            </p>
            <p className="mt-4 text-sm leading-relaxed text-ink-faint">
              Every sub-check is explainable: you see exactly which rule moved the score, not
              just the total.
            </p>
          </Reveal>

          <Reveal delay={0.1} className="rounded-2xl border border-border bg-surface p-6 sm:p-8">
            <div className="flex items-center gap-6">
              <div className="relative flex h-24 w-24 shrink-0 items-center justify-center">
                <svg viewBox="0 0 100 100" className="h-24 w-24 -rotate-90">
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
                      <stop offset="0%" stopColor="var(--color-ember-soft)" />
                      <stop offset="100%" stopColor="var(--color-rose)" />
                    </linearGradient>
                  </defs>
                </svg>
                <span className="absolute text-2xl font-semibold text-ink">{overall}</span>
              </div>
              <div>
                <p className="text-sm font-medium text-ink">Overall ATS score</p>
                <p className="mt-1 text-sm text-ink-faint">
                  Weighted average of ten checks, sample below.
                </p>
              </div>
            </div>

            <ul className="mt-8 space-y-5">
              {atsChecks.map((check) => (
                <li key={check.label}>
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-ink-muted">{check.label}</span>
                    <span className="font-medium text-ink">{check.score}/100</span>
                  </div>
                  <div className="mt-1.5 h-1.5 w-full overflow-hidden rounded-full bg-surface-2">
                    <div
                      className="h-full rounded-full bg-linear-to-r from-ember-soft to-rose"
                      style={{ width: `${check.score}%` }}
                    />
                  </div>
                </li>
              ))}
            </ul>
          </Reveal>
        </div>
      </div>
    </section>
  );
}
