/** The only two generation flows `OutputTypePage` can actually navigate to (ADR-033) — JD
 *  optimization and application email. Deliberately narrower than `GenerationType`
 *  (services/applicationApi.ts), which still carries `RESUME_ONLY`/`COVER_LETTER_ONLY`/`ALL`
 *  for application-service's own historical contract; `JD_OPTIMIZATION` isn't a `GenerationType`
 *  at all (it's a jd-service concept, not an Application one — see OutputTypePage's own
 *  comment), so casting the `?type=` query param to that type was never actually safe. */
export type GenerationFlowType = 'JD_OPTIMIZATION' | 'EMAIL_ONLY';

interface GenerationTypeConfig {
  badgeLabel: string;
  generatingLabel: string;
  requirements: string[];
}

const CONFIG: Record<GenerationFlowType, GenerationTypeConfig> = {
  JD_OPTIMIZATION: {
    badgeLabel: 'JD Optimization',
    generatingLabel: 'JD optimization',
    requirements: ['Job description', 'Profile'],
  },
  EMAIL_ONLY: {
    badgeLabel: 'Email',
    generatingLabel: 'Application email',
    requirements: ['Job description', 'Profile'],
  },
};

/** Small context badge next to the page heading (redesign spec &sect;13) — makes it obvious
 *  what this run of the wizard will produce before the user even looks at the JD workspace. */
export function GenerationTypeBadge({ generationType }: { generationType: GenerationFlowType }) {
  const config = CONFIG[generationType];
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full border border-border-strong bg-surface-2 px-3 py-1 text-xs font-medium text-ink">
      {config.badgeLabel}
    </span>
  );
}

/**
 * Right-hand "Job information" + "Application context" panel (redesign spec &sect;10-11 & 16) —
 * the same component behind both generation types, only its config (above) changes per
 * `generationType`. Job title/company/location are deliberately never guessed here: nothing has
 * been extracted from the JD yet at this step (JD analysis only runs after it's confirmed on
 * Review), so this honestly shows the "not yet detected" state instead of inventing a preview.
 *
 * <p>Neither flow picks a template here — JD optimization's template selection happens later,
 * at the JD-optimization result page's own "Choose your template" section, reading from the
 * user's saved template library (ADR-034); this step never carried a `templateId` of its own.
 */
export function JobContextPanel({ generationType }: { generationType: GenerationFlowType }) {
  const config = CONFIG[generationType];

  return (
    <div className="flex flex-col gap-6">
      <div className="rounded-2xl border border-border bg-surface p-6">
        <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">Job information</p>
        <p className="mt-3 text-sm text-ink-muted">Job information will be detected after submission.</p>
      </div>

      <div className="rounded-2xl border border-border bg-surface p-6">
        <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">Generating</p>
        <p className="mt-2 text-base font-semibold text-ink">{config.generatingLabel}</p>

        <div className="mt-4">
          <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">Uses</p>
          <ul className="mt-2 space-y-1.5">
            {config.requirements.map((req) => (
              <li key={req} className="flex items-center gap-2 text-sm text-ink-muted">
                <span className="h-1.5 w-1.5 rounded-full bg-ember-soft" aria-hidden="true" />
                {req}
              </li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  );
}
