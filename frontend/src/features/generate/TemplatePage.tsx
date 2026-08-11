import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { FullScreenSpinner } from '@/components/ui/FullScreenSpinner';
import { listTemplates } from '@/services/templateApi';
import { TemplatePreview } from './components/TemplatePreview';
import { GenerateLayout } from './GenerateLayout';

/**
 * Template selection — Phase 1 of the template system (ARCHITECTURE_DECISIONS.md ADR-016).
 * Only built-in templates are selectable today; upload and online are shown honestly as not
 * yet available rather than faked. The chosen templateId travels to /generate/processing as a
 * query param, which resume-service persists on the generation and every derived resume
 * version.
 *
 * `type` (set by OutputTypePage, carried here by ReviewPage) travels the same way — `ALL`
 * ("Generate All") also picks a resume template, unlike email/cover-letter-only, which skip
 * this step entirely (see ProcessingPage).
 */
export function TemplatePage() {
  const { jdId = '' } = useParams<{ jdId: string }>();
  const [searchParams] = useSearchParams();
  const generationType = searchParams.get('type') ?? 'RESUME_ONLY';
  // Set when arriving from the Templates page's "Use this template" action — preselected below
  // once the real catalogue has loaded and confirmed it's a template that actually exists,
  // rather than trusting the query param blindly.
  const preselectedTemplateId = searchParams.get('templateId');
  const navigate = useNavigate();
  const [selected, setSelected] = useState<string | null>(null);

  const templatesQuery = useQuery({ queryKey: ['templates', 'RESUME'], queryFn: () => listTemplates('RESUME') });

  useEffect(() => {
    if (selected || !preselectedTemplateId || !templatesQuery.data) return;
    if (templatesQuery.data.some((t) => t.templateId === preselectedTemplateId)) {
      setSelected(preselectedTemplateId);
    }
  }, [preselectedTemplateId, templatesQuery.data, selected]);

  if (templatesQuery.isLoading) {
    return <FullScreenSpinner label="Loading templates…" />;
  }

  return (
    <GenerateLayout
      activeStep={3}
      title="Choose a template"
      subtitle="Pick the layout your resume will be generated with."
    >
      {templatesQuery.isError && <ErrorBanner error={templatesQuery.error} />}

      {templatesQuery.data && (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {templatesQuery.data.map((template) => {
              const isSelected = selected === template.templateId;
              return (
                <button
                  key={template.templateId}
                  type="button"
                  onClick={() => setSelected(template.templateId)}
                  aria-pressed={isSelected}
                  aria-label={`${template.name} template${isSelected ? ' (selected)' : ''}`}
                  className={`flex flex-col gap-3 rounded-2xl border p-4 text-left transition-colors ${
                    isSelected
                      ? 'border-ember-soft ring-2 ring-ember-soft/40'
                      : 'border-border-strong hover:border-ink-muted'
                  }`}
                >
                  <TemplatePreview previewKey={template.previewKey} />
                  <div>
                    <div className="flex items-center justify-between gap-2">
                      <h3 className="text-sm font-semibold text-ink">{template.name}</h3>
                      {isSelected && (
                        <span className="flex h-5 w-5 items-center justify-center rounded-full bg-ember-soft text-xs text-void">
                          ✓
                        </span>
                      )}
                    </div>
                    <p className="mt-1 text-xs leading-relaxed text-ink-muted">{template.description}</p>
                    {template.atsSafe && (
                      <span className="mt-2 inline-flex items-center gap-1 rounded-full bg-mint/10 px-2 py-0.5 text-[11px] font-medium text-mint">
                        ATS-safe
                      </span>
                    )}
                  </div>
                </button>
              );
            })}
          </div>

          <div className="mt-6 grid gap-4 sm:grid-cols-2">
            <div className="rounded-2xl border border-border bg-surface/60 p-5 opacity-60">
              <h3 className="text-sm font-medium text-ink">Upload your own template</h3>
              <p className="mt-1.5 text-xs text-ink-faint">
                Needs a document renderer that can actually use an uploaded file — not built yet.
              </p>
              <span className="mt-3 inline-flex w-fit items-center gap-1.5 rounded-full border border-border-strong px-2.5 py-1 text-xs text-ink-faint">
                🔒 Coming Soon
              </span>
            </div>
            <div className="rounded-2xl border border-border bg-surface/60 p-5 opacity-60">
              <h3 className="text-sm font-medium text-ink">Browse online templates</h3>
              <p className="mt-1.5 text-xs text-ink-faint">
                No template provider is connected yet — nothing here is faked or downloaded.
              </p>
              <span className="mt-3 inline-flex w-fit items-center gap-1.5 rounded-full border border-border-strong px-2.5 py-1 text-xs text-ink-faint">
                🔒 Coming Soon
              </span>
            </div>
          </div>

          <div className="mt-6 flex justify-end">
            <Button
              disabled={!selected}
              onClick={() => navigate(`/generate/processing/${jdId}?templateId=${selected}&type=${generationType}`)}
            >
              {generationType === 'ALL' ? 'Generate everything' : 'Generate my resume'}
            </Button>
          </div>
        </>
      )}
    </GenerateLayout>
  );
}
