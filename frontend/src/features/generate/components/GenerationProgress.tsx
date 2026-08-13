export const GENERATION_STEPS = ['Output', 'Job description', 'Review', 'Generate'] as const;

/**
 * The four-step workflow indicator, shared by every generation type (point 5 of the redesign
 * spec) — Resume, Cover Letter, Email and "Generate All" all confirm a template (when they have
 * one) inside the Review step itself now, so there is no separate "Template" step to branch on
 * here the way GenerateLayout used to.
 *
 * Desktop: full horizontal rail with connecting lines. Mobile: a compact "Step N of 4 — label"
 * strip plus a slim progress bar, so the four labels never wrap or force horizontal scroll on a
 * narrow screen.
 */
export function GenerationProgress({ activeStep }: { activeStep: number }) {
  return (
    <nav aria-label="Generation progress">
      {/* Desktop / tablet */}
      <ol className="hidden items-center gap-2 text-xs text-ink-faint sm:flex">
        {GENERATION_STEPS.map((step, index) => (
          <li key={step} className="flex items-center gap-2">
            <span
              className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full border text-[11px] font-semibold transition-colors ${
                index === activeStep
                  ? 'border-ember-soft text-ember-soft'
                  : index < activeStep
                    ? 'border-mint text-mint'
                    : 'border-border text-ink-faint'
              }`}
            >
              {index < activeStep ? '✓' : index + 1}
            </span>
            <span className={index === activeStep ? 'font-medium text-ink' : ''}>{step}</span>
            {index < GENERATION_STEPS.length - 1 && (
              <span
                className={`mx-1 h-px w-8 transition-colors lg:w-12 ${index < activeStep ? 'bg-mint/40' : 'bg-border'}`}
                aria-hidden="true"
              />
            )}
          </li>
        ))}
      </ol>

      {/* Mobile: compact step indicator */}
      <div className="sm:hidden">
        <p className="text-xs font-medium text-ink-faint">
          Step {activeStep + 1} of {GENERATION_STEPS.length} · <span className="text-ink">{GENERATION_STEPS[activeStep]}</span>
        </p>
        <div className="mt-2 flex gap-1.5">
          {GENERATION_STEPS.map((step, index) => (
            <span
              key={step}
              className={`h-1.5 flex-1 rounded-full transition-colors ${
                index <= activeStep ? 'bg-ember-soft' : 'bg-border'
              }`}
            />
          ))}
        </div>
      </div>
    </nav>
  );
}
