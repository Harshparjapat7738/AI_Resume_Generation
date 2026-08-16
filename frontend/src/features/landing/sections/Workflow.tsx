import { Reveal } from '../components/Reveal';
import { ArrowRightIcon, ChecklistIcon, SearchIcon, TrophyIcon, UploadIcon } from '../components/icons';
import { howItWorks } from '../content';

const icons = [UploadIcon, SearchIcon, ChecklistIcon, TrophyIcon];

export function Workflow() {
  return (
    <section id="workflow" className="scroll-mt-20 border-t border-border bg-surface py-20 sm:py-24">
      <div className="mx-auto max-w-6xl px-6">
        <Reveal className="mx-auto max-w-2xl text-center">
          <p className="text-sm font-semibold uppercase tracking-wide text-brand">How it works</p>
          <h2 className="mt-3 text-3xl font-semibold tracking-tight text-ink sm:text-4xl">
            Your journey to a grounded application
          </h2>
          <p className="mt-3 text-ink-muted">Simple steps. Honest results.</p>
        </Reveal>

        <Reveal
          stagger
          className="mt-14 flex flex-col gap-5 lg:flex-row lg:items-stretch lg:gap-3"
        >
          {howItWorks.map((step, i) => {
            const Icon = icons[i % icons.length]!;
            return (
              <div key={step.index} className="contents lg:flex lg:flex-1 lg:items-stretch lg:gap-3">
                <div className="relative flex-1 rounded-2xl border border-border bg-void p-6 transition-all hover:-translate-y-1 hover:border-brand-soft hover:shadow-xl hover:shadow-brand/10">
                  <div className="flex items-center gap-3">
                    <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-linear-to-br from-brand/15 to-brand-2/15 text-brand">
                      <Icon className="h-5 w-5" />
                    </span>
                    <span className="flex h-6 w-6 items-center justify-center rounded-full bg-brand text-[11px] font-semibold text-void">
                      {i + 1}
                    </span>
                  </div>
                  <h3 className="mt-4 text-base font-semibold text-ink">{step.title}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-ink-faint">{step.description}</p>
                </div>

                {i < howItWorks.length - 1 && (
                  <div className="hidden shrink-0 items-center justify-center lg:flex" aria-hidden="true">
                    <ArrowRightIcon className="h-4 w-4 text-border-strong" />
                  </div>
                )}
              </div>
            );
          })}
        </Reveal>
      </div>
    </section>
  );
}
