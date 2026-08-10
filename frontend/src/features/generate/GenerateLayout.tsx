import type { ReactNode } from 'react';
import { AppHeader } from '@/components/layout/AppHeader';

const DEFAULT_STEPS = ['Output', 'Job description', 'Review', 'Template', 'Generate'] as const;

/** No template step — email generation has nothing to select a template for. */
export const EMAIL_STEPS = ['Output', 'Job description', 'Review', 'Generate'] as const;

/** No template step — cover-letter generation has nothing to select a template for either
 *  (docs/DATABASE.md's `templates.type` reserves `COVER_LETTER` for later; no rows exist
 *  yet, see ARCHITECTURE_DECISIONS.md ADR-020). */
export const COVER_LETTER_STEPS = ['Output', 'Job description', 'Review', 'Generate'] as const;

export function GenerateLayout({
  activeStep,
  title,
  subtitle,
  children,
  steps = DEFAULT_STEPS,
}: {
  activeStep: number;
  title: string;
  subtitle: string;
  children: ReactNode;
  steps?: readonly string[] | undefined;
}) {
  return (
    <div className="min-h-screen bg-void">
      <AppHeader />
      <main className="mx-auto max-w-2xl px-6 py-12">
        <ol className="mb-10 flex items-center gap-2 text-xs text-ink-faint">
          {steps.map((step, index) => (
            <li key={step} className="flex items-center gap-2">
              <span
                className={`flex h-6 w-6 items-center justify-center rounded-full border text-[11px] font-semibold ${
                  index === activeStep
                    ? 'border-ember-soft text-ember-soft'
                    : index < activeStep
                      ? 'border-mint text-mint'
                      : 'border-border text-ink-faint'
                }`}
              >
                {index < activeStep ? '✓' : index + 1}
              </span>
              <span className={index === activeStep ? 'text-ink' : ''}>{step}</span>
              {index < steps.length - 1 && <span className="mx-1 h-px w-6 bg-border" aria-hidden="true" />}
            </li>
          ))}
        </ol>

        <h1 className="text-2xl font-semibold tracking-tight text-ink">{title}</h1>
        <p className="mt-1.5 text-sm text-ink-muted">{subtitle}</p>

        <div className="mt-8">{children}</div>
      </main>
    </div>
  );
}
