import { Reveal } from '../components/Reveal';
import { workflowSteps } from '../content';

export function Workflow() {
  return (
    <section id="workflow" className="scroll-mt-20 border-t border-border bg-surface py-24">
      <div className="mx-auto max-w-6xl px-6">
        <Reveal className="max-w-2xl">
          <p className="text-sm font-medium uppercase tracking-wide text-ember-soft">How it works</p>
          <h2 className="mt-3 text-3xl font-semibold tracking-tight text-ink sm:text-4xl">
            Five steps from profile to a defensible application.
          </h2>
        </Reveal>

        <div className="relative mt-14">
          <div
            aria-hidden="true"
            className="absolute left-[19px] top-2 bottom-2 hidden w-px bg-border sm:block"
          />
          <Reveal stagger className="flex flex-col gap-10">
            {workflowSteps.map((step) => (
              <div key={step.index} className="relative flex gap-6 sm:gap-8">
                <div className="relative z-10 flex h-10 w-10 shrink-0 items-center justify-center rounded-full border border-border-strong bg-void text-xs font-semibold text-ember-soft">
                  {step.index}
                </div>
                <div className="pb-2">
                  <h3 className="text-lg font-medium text-ink">{step.title}</h3>
                  <p className="mt-1.5 max-w-xl text-sm leading-relaxed text-ink-muted">
                    {step.description}
                  </p>
                </div>
              </div>
            ))}
          </Reveal>
        </div>
      </div>
    </section>
  );
}
