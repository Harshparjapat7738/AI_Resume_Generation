import { useMemo, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { formatDate } from '@/features/dashboard/utils';
import { applicationsToResumeItems, mergeResumeItemsByDate, standaloneToResumeItems } from '@/features/resumes/resumeListUtils';
import { listApplications } from '@/services/applicationApi';
import { downloadDocument, generateFromCustomTemplate } from '@/services/documentApi';
import { listResumes } from '@/services/resumeApi';
import type { Template } from '@/services/templateApi';

/**
 * "Use this template" for a custom RESUME template: pick one of the user's already-generated
 * resumes, mail-merge it into this template, and download the result. Reuses the same merged
 * (application-linked + standalone) resume list ResumesPage shows in full — see
 * resumeListUtils.ts — so this modal never has a different idea of "your resumes" than the
 * dedicated page does.
 */
export function GenerateFromCustomTemplateModal({ template, onClose }: { template: Template; onClose: () => void }) {
  const [selectedKey, setSelectedKey] = useState<string | null>(null);

  const applicationsQuery = useQuery({ queryKey: ['applications', 'summary'], queryFn: () => listApplications(undefined, 0, 200) });
  const resumesQuery = useQuery({ queryKey: ['resumes', 'all'], queryFn: () => listResumes(0, 200) });

  const resumes = useMemo(
    () =>
      mergeResumeItemsByDate(
        applicationsToResumeItems(applicationsQuery.data?.content ?? []),
        standaloneToResumeItems(resumesQuery.data?.content ?? []),
      ),
    [applicationsQuery.data, resumesQuery.data],
  );

  const isLoading = applicationsQuery.isLoading || resumesQuery.isLoading;
  const selected = resumes.find((r) => r.key === selectedKey) ?? null;

  const generateMutation = useMutation({
    mutationFn: async () => {
      if (!selected) throw new Error('No resume selected');
      const rendered = await generateFromCustomTemplate(template.templateId, selected.resumeVersionId, template.fieldMappings ?? {});
      const blob = await downloadDocument(rendered.id);
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `${template.name.replace(/[^a-z0-9 _-]/gi, '').trim() || 'resume'}.docx`;
      link.click();
      URL.revokeObjectURL(url);
    },
  });

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-void/70 backdrop-blur-sm animate-toast-in" onClick={onClose} />
      <div className="animate-card-in relative flex max-h-[85vh] w-full max-w-lg flex-col overflow-hidden rounded-2xl border border-border bg-surface shadow-2xl">
        <div className="flex items-center justify-between border-b border-border px-6 py-4">
          <div>
            <h2 className="text-base font-semibold text-ink">Use "{template.name}"</h2>
            <p className="mt-0.5 text-xs text-ink-faint">Choose which resume's content to apply this template to.</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="flex h-8 w-8 items-center justify-center rounded-lg text-ink-muted hover:text-ink"
          >
            ✕
          </button>
        </div>

        <div className="flex-1 overflow-y-auto px-6 py-5">
          {isLoading && <p className="text-sm text-ink-faint">Loading your resumes…</p>}
          {!isLoading && resumes.length === 0 && (
            <EmptyState
              title="You don't have a resume yet."
              hint="Generate a resume first, then come back to apply this template to it."
              action={
                <Link to="/generate/job?type=RESUME_ONLY">
                  <Button className="!px-5 !py-2.5 !text-sm">Generate a resume</Button>
                </Link>
              }
            />
          )}
          {resumes.length > 0 && (
            <ul className="space-y-2">
              {resumes.map((item) => (
                <li key={item.key}>
                  <button
                    type="button"
                    onClick={() => setSelectedKey(item.key)}
                    aria-pressed={selectedKey === item.key}
                    className={`flex w-full items-center justify-between gap-3 rounded-xl border px-4 py-3 text-left transition-colors ${
                      selectedKey === item.key ? 'border-ember-soft ring-2 ring-ember-soft/30' : 'border-border-strong hover:border-ink-muted'
                    }`}
                  >
                    <span className="min-w-0">
                      <span className="block truncate text-sm font-medium text-ink">{item.jobTitle ?? 'Untitled role'}</span>
                      <span className="block truncate text-xs text-ink-faint">
                        {item.company ?? 'Unknown company'} · {formatDate(item.createdAt)}
                      </span>
                    </span>
                    {selectedKey === item.key && (
                      <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-ember-soft text-xs text-void">✓</span>
                    )}
                  </button>
                </li>
              ))}
            </ul>
          )}
          {generateMutation.isError && <div className="mt-4"><ErrorBanner error={generateMutation.error} /></div>}
        </div>

        {resumes.length > 0 && (
          <div className="flex justify-end border-t border-border px-6 py-4">
            <Button
              type="button"
              className="!px-5 !py-2.5 !text-sm"
              disabled={!selected}
              loading={generateMutation.isPending}
              onClick={() => generateMutation.mutate()}
            >
              Generate &amp; download
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}
