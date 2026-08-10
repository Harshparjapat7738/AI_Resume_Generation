import { Reveal } from '../components/Reveal';
import { ArrowRightIcon } from '../components/icons';

export function FinalCta() {
  return (
    <section className="relative overflow-hidden border-t border-border bg-void py-24">
      <div className="absolute inset-0 bg-forge-glow" aria-hidden="true" />
      <div className="relative mx-auto max-w-3xl px-6 text-center">
        <Reveal>
          <h2 className="text-3xl font-semibold tracking-tight text-ink sm:text-4xl">
            Build an application you can defend, line by line.
          </h2>
          <p className="mt-4 text-lg leading-relaxed text-ink-muted">
            One verified profile. Every job description. Nothing invented, nothing sent without
            your say-so.
          </p>
        </Reveal>

        <Reveal delay={0.1}>
          <div className="mt-9 flex flex-col items-center gap-3 sm:flex-row sm:justify-center">
            <a
              href="#top"
              className="group inline-flex items-center justify-center gap-2 rounded-full bg-linear-to-r from-ember-soft to-rose px-6 py-3 text-sm font-semibold text-void transition-transform hover:-translate-y-0.5"
            >
              Back to the top
              <ArrowRightIcon className="h-4 w-4 -rotate-90 transition-transform group-hover:-translate-y-0.5" />
            </a>
          </div>
          <p className="mt-5 text-xs text-ink-faint">
            Milestone 1 of 9 — the platform foundation is live. Profile, JD and generation
            screens ship in upcoming milestones.
          </p>
        </Reveal>
      </div>
    </section>
  );
}
