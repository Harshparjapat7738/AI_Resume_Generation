/** Kept for the Email flow, which has no template step and never did. The resume flow that
 *  once owned `GENERATION_STEPS_WITH_TEMPLATE` was removed with document generation
 *  (ADR-033); JD optimization uses `GENERATION_STEPS_OPTIMIZE`. */
export const GENERATION_STEPS_WITH_TEMPLATE = ['Output', 'Job description', 'Review', 'Template', 'Generate'] as const;
export const GENERATION_STEPS_NO_TEMPLATE = ['Output', 'Job description', 'Review', 'Generate'] as const;
/** JD optimization (ADR-033) — no template step, because it produces data rather than a
 *  document, and the final step is named for what it actually does. */
export const GENERATION_STEPS_OPTIMIZE = ['Output', 'Job description', 'Review', 'Optimize'] as const;

/** Default for pages that render before a generation type is known (OutputTypePage itself) —
 *  the 5-step list, since Resume/"Generate All" (the two types with a Template step) are the
 *  common case. Pages that already know `type` should pass `stepsForGenerationType(type)`
 *  instead so a Cover-Letter/Email flow never shows a step it doesn't have. */
export const GENERATION_STEPS = GENERATION_STEPS_WITH_TEMPLATE;

export function stepsForGenerationType(generationType: string): readonly string[] {
  if (generationType === 'JD_OPTIMIZATION') return GENERATION_STEPS_OPTIMIZE;
  return generationType === 'EMAIL_ONLY' || generationType === 'COVER_LETTER_ONLY'
    ? GENERATION_STEPS_NO_TEMPLATE
    : GENERATION_STEPS_WITH_TEMPLATE;
}

/**
 * The workflow indicator, shared by every generation type (point 5 of the redesign spec).
 * JD optimization ends at an "Optimize" step; Email goes straight from Review to Generate.
 * Neither picks a template — that step was removed with document generation (ADR-033).
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
