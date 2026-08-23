/**
 * The one workflow shape every generation type shares: enter the JD, see and close skill gaps,
 * pick what to generate, then generate it. No Confirm/Review step exists any more (removed
 * entirely, not hidden) and no per-generation-type step-count variants exist either — Resume,
 * Email, Cover Letter and "All" all move through the exact same four steps, so there is nothing
 * left to select a step list by type any more.
 */
export const GENERATION_STEPS = ['Job Description', 'Skill Gap', 'Output Type', 'Generate'] as const;

/**
 * The workflow indicator, shared by every generation type.
 *
 * Desktop: full horizontal rail with connecting lines. Mobile: a compact "Step N of {total} —
 * label" strip plus a slim progress bar, so the labels never wrap or force horizontal scroll on
 * a narrow screen.
 */
export function GenerationProgress({
  activeStep,
  steps = GENERATION_STEPS,
}: {
  activeStep: number;
  steps?: readonly string[];
}) {
  return (
    <nav aria-label="Generation progress">
      {/* Desktop / tablet */}
      <ol className="hidden items-center gap-2 text-xs text-ink-faint sm:flex">
        {steps.map((step, index) => (
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
            {index < steps.length - 1 && (
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
          Step {activeStep + 1} of {steps.length} · <span className="text-ink">{steps[activeStep]}</span>
        </p>
        <div className="mt-2 flex gap-1.5">
          {steps.map((step, index) => (
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
