import { useQuery } from '@tanstack/react-query';
import type { GenerationType } from '@/services/applicationApi';
import { getTemplate } from '@/services/templateApi';

interface GenerationTypeConfig {
  badgeLabel: string;
  badgeIcon?: string;
  generatingLabel: string;
  requirements: string[];
  packageItems?: { label: string; note: string }[];
}

const CONFIG: Record<GenerationType, GenerationTypeConfig> = {
  RESUME_ONLY: {
    badgeLabel: 'Resume',
    generatingLabel: 'Resume',
    requirements: ['Job description', 'Profile', 'Template'],
  },
  COVER_LETTER_ONLY: {
    badgeLabel: 'Cover Letter',
    generatingLabel: 'Cover letter',
    requirements: ['Job description', 'Profile'],
  },
  EMAIL_ONLY: {
    badgeLabel: 'Email',
    generatingLabel: 'Application email',
    requirements: ['Job description', 'Profile'],
  },
  ALL: {
    badgeLabel: 'Complete Application',
    badgeIcon: '✨',
    generatingLabel: 'Complete application',
    requirements: ['Job description', 'Profile', 'Template'],
    packageItems: [
      { label: 'Resume', note: 'Built from your selected template' },
      { label: 'Cover letter', note: 'Grounded in the same profile evidence' },
      { label: 'Email', note: 'Ready to send alongside them' },
      { label: 'ATS / JD analysis', note: 'Computed once the resume is generated' },
    ],
  },
};

/** Small context badge next to the page heading (redesign spec &sect;13) — makes it obvious
 *  what this run of the wizard will produce before the user even looks at the JD workspace. */
export function GenerationTypeBadge({ generationType }: { generationType: GenerationType }) {
  const config = CONFIG[generationType];
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full border border-border-strong bg-surface-2 px-3 py-1 text-xs font-medium text-ink">
      {config.badgeIcon && <span aria-hidden="true">{config.badgeIcon}</span>}
      {config.badgeLabel}
    </span>
  );
}

/**
 * Right-hand "Job information" + "Application context" panel (redesign spec &sect;10-11 & 16) —
 * the same component behind all four generation types, only its config (above) and the
 * template-preview query change per `generationType`. Job title/company/location are
 * deliberately never guessed here: nothing has been extracted from the JD yet at this step (JD
 * analysis only runs after it's confirmed on Review), so this honestly shows the "not yet
 * detected" state instead of inventing a preview.
 */
export function JobContextPanel({
  generationType,
  preselectedTemplateId,
}: {
  generationType: GenerationType;
  preselectedTemplateId: string | null;
}) {
  const config = CONFIG[generationType];
  const needsTemplate = generationType === 'RESUME_ONLY' || generationType === 'ALL';

  const templateQuery = useQuery({
    queryKey: ['template', preselectedTemplateId],
    queryFn: () => getTemplate(preselectedTemplateId as string),
    enabled: needsTemplate && Boolean(preselectedTemplateId),
    retry: false,
  });

  return (
    <div className="flex flex-col gap-6">
      <div className="rounded-2xl border border-border bg-surface p-6">
        <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">Job information</p>
        <p className="mt-3 text-sm text-ink-muted">Job information will be detected after submission.</p>
      </div>

      <div className="rounded-2xl border border-border bg-surface p-6">
        <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">
          {generationType === 'ALL' ? 'Application package' : 'Generating'}
        </p>
        <p className="mt-2 text-base font-semibold text-ink">{config.generatingLabel}</p>

        {config.packageItems ? (
          <>
            <ul className="mt-4 space-y-2.5">
              {config.packageItems.map((item) => (
                <li key={item.label} className="flex items-start gap-2.5 text-sm">
                  <span className="mt-0.5 text-mint" aria-hidden="true">
                    ✓
                  </span>
                  <div>
                    <span className="text-ink">{item.label}</span>
                    <p className="text-xs text-ink-faint">{item.note}</p>
                  </div>
                </li>
              ))}
            </ul>
            <p className="mt-4 border-t border-border pt-4 text-xs text-ink-faint">
              One application will contain all generated outputs — resume, cover letter, email and analysis all
              share the same applicationId.
            </p>
          </>
        ) : (
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
        )}

        {needsTemplate && (
          <div className="mt-4 border-t border-border pt-4">
            <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">Template</p>
            {preselectedTemplateId && templateQuery.data ? (
              <div className="mt-1.5 flex items-center justify-between gap-2">
                <span className="text-sm text-ink">{templateQuery.data.name}</span>
                <span className="rounded-full bg-surface-2 px-2 py-0.5 text-[11px] text-ink-faint">
                  {templateQuery.data.source === 'CUSTOM_UPLOAD' ? 'Your upload' : templateQuery.data.type}
                </span>
              </div>
            ) : (
              <p className="mt-1.5 text-sm text-ink-faint">Selected on the next step.</p>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
