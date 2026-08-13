import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Select } from '@/components/ui/Select';
import { DashboardShell } from '@/features/dashboard/components/DashboardShell';
import { FilterChipGroup } from '@/features/dashboard/components/FilterChipGroup';
import { PageHeader } from '@/features/dashboard/components/PageHeader';
import { SearchInput } from '@/features/dashboard/components/SearchInput';
import { GridIcon, PlusCircleIcon } from '@/features/dashboard/icons';
import { listTemplates, type Template, type TemplateDocumentType } from '@/services/templateApi';
import { CustomTemplateCard } from './components/CustomTemplateCard';
import { EditMappingModal } from './components/EditMappingModal';
import { GenerateFromCustomTemplateModal } from './components/GenerateFromCustomTemplateModal';
import { TemplateCard } from './components/TemplateCard';
import { TemplatePreviewModal } from './components/TemplatePreviewModal';
import { UploadTemplateWizard } from './components/UploadTemplateWizard';

type SourceTab = 'BUILT_IN' | 'CUSTOM';
type SortBy = 'newest' | 'oldest' | 'name-asc' | 'name-desc';

const CATEGORIES: { id: TemplateDocumentType; label: string; generationType: string }[] = [
  { id: 'RESUME', label: 'Resume', generationType: 'RESUME_ONLY' },
  { id: 'COVER_LETTER', label: 'Cover Letter', generationType: 'COVER_LETTER_ONLY' },
  { id: 'EMAIL', label: 'Email', generationType: 'EMAIL_ONLY' },
];

const SORT_OPTIONS: { id: SortBy; label: string }[] = [
  { id: 'newest', label: 'Newest' },
  { id: 'oldest', label: 'Oldest' },
  { id: 'name-asc', label: 'Name A–Z' },
  { id: 'name-desc', label: 'Name Z–A' },
];

function sortTemplates(items: Template[], sortBy: SortBy): Template[] {
  const copy = [...items];
  if (sortBy === 'name-asc') return copy.sort((a, b) => a.name.localeCompare(b.name));
  if (sortBy === 'name-desc') return copy.sort((a, b) => b.name.localeCompare(a.name));
  copy.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
  return sortBy === 'oldest' ? copy.reverse() : copy;
}

/** Skeleton document cards (redesign spec &sect;21) — same card shape as the real thing, never
 *  a blank grid while the catalogue itself is still loading. */
function CardSkeletonGrid() {
  return (
    <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
      {Array.from({ length: 4 }).map((_, i) => (
        <div key={i} className="animate-pulse overflow-hidden rounded-2xl border border-border-strong">
          <div className="aspect-[8.5/11] w-full bg-surface-2" />
          <div className="space-y-2 p-4">
            <div className="h-3 w-2/3 rounded bg-surface-2" />
            <div className="h-2 w-1/3 rounded bg-surface-2" />
            <div className="mt-3 h-8 w-full rounded-full bg-surface-2" />
          </div>
        </div>
      ))}
    </div>
  );
}

/**
 * The complete template library — built-in (resume-service's seeded catalogue) and a user's own
 * uploaded custom templates, side by side. Both come back from the same real `listTemplates`
 * call (resume-service scopes custom rows to the caller). Every card's preview is the real,
 * rendered document (see `DocumentPreview`) — the same `PdfRenderer`/mail-merge pipeline that
 * renders the final generated document, fed fixed sample data instead of a real profile — never
 * a CSS mockup, and never a filename standing in for a design (redesign brief &sect;1-2/25).
 */
export function TemplatesPage() {
  const [searchParams] = useSearchParams();
  // Optional deep-link: a caller (e.g. a future "change template" action elsewhere) can open
  // this page with a specific template highlighted — this page itself never sets its own
  // persistent "current selection", since a template only really becomes "selected" once it's
  // attached to an in-progress generation (see TemplatePage, the wizard's own picker step).
  const highlightedTemplateId = searchParams.get('selected');

  const [category, setCategory] = useState<TemplateDocumentType>('RESUME');
  const [sourceTab, setSourceTab] = useState<SourceTab>('BUILT_IN');
  const [search, setSearch] = useState('');
  const [sortBy, setSortBy] = useState<SortBy>('newest');
  const [showUploadWizard, setShowUploadWizard] = useState(false);
  const [previewTemplate, setPreviewTemplate] = useState<Template | null>(null);
  const [editMappingTemplate, setEditMappingTemplate] = useState<Template | null>(null);
  const [generateTemplate, setGenerateTemplate] = useState<Template | null>(null);

  const activeCategory = CATEGORIES.find((c) => c.id === category)!;

  const templatesQuery = useQuery({
    queryKey: ['templates', category],
    queryFn: () => listTemplates(category),
  });

  const bySource = useMemo(() => {
    const all = templatesQuery.data ?? [];
    return all.filter((t) => (sourceTab === 'BUILT_IN' ? t.source === 'BUILT_IN' : t.source === 'CUSTOM_UPLOAD'));
  }, [templatesQuery.data, sourceTab]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    const matches = q ? bySource.filter((t) => t.name.toLowerCase().includes(q) || t.description.toLowerCase().includes(q)) : bySource;
    return sortTemplates(matches, sortBy);
  }, [bySource, search, sortBy]);

  const customCount = (templatesQuery.data ?? []).filter((t) => t.source === 'CUSTOM_UPLOAD').length;

  const previewPrimaryAction = previewTemplate
    ? previewTemplate.source === 'BUILT_IN'
      ? { label: 'Use this template', href: `/generate/job?type=${activeCategory.generationType}&templateId=${previewTemplate.templateId}` }
      : previewTemplate.type === 'RESUME'
        ? { label: 'Use this template', onClick: () => setGenerateTemplate(previewTemplate) }
        : {
            label: 'Generation coming soon',
            disabled: true,
            disabledReason: 'Generation for cover letters and emails is coming soon.',
          }
    : undefined;

  return (
    <DashboardShell>
      {({ user, onLogout }) => (
        <>
          <PageHeader
            title="Templates"
            description="Browse built-in layouts, or upload your own — CareerForge fills in your real content while keeping your design exactly as you made it."
            user={user}
            onLogout={onLogout}
            action={
              <Button className="!px-4 !py-2.5 !text-sm" onClick={() => setShowUploadWizard(true)}>
                <PlusCircleIcon className="h-4 w-4" />
                Upload custom template
              </Button>
            }
          />

          <div className="mt-6 flex flex-wrap items-center gap-3">
            <div className="inline-flex rounded-full border border-border bg-surface p-1 text-sm">
              <button
                type="button"
                onClick={() => setSourceTab('BUILT_IN')}
                className={`rounded-full px-4 py-1.5 transition-colors ${sourceTab === 'BUILT_IN' ? 'bg-ink text-void' : 'text-ink-muted hover:text-ink'}`}
              >
                Built-in Templates
              </button>
              <button
                type="button"
                onClick={() => setSourceTab('CUSTOM')}
                className={`rounded-full px-4 py-1.5 transition-colors ${sourceTab === 'CUSTOM' ? 'bg-ink text-void' : 'text-ink-muted hover:text-ink'}`}
              >
                My Templates{customCount > 0 ? ` (${customCount})` : ''}
              </button>
            </div>
          </div>

          <Card className="mt-4">
            <div className="flex flex-wrap items-center justify-between gap-4">
              <FilterChipGroup options={CATEGORIES.map((c) => ({ id: c.id, label: c.label }))} value={category} onChange={setCategory} />
              <div className="flex flex-wrap items-center gap-3">
                <SearchInput value={search} onChange={setSearch} placeholder="Search templates…" />
                <Select label="" aria-label="Sort by" value={sortBy} onChange={(e) => setSortBy(e.target.value as SortBy)} className="!mt-0 !w-40 !py-2">
                  {SORT_OPTIONS.map((o) => (
                    <option key={o.id} value={o.id}>
                      Sort: {o.label}
                    </option>
                  ))}
                </Select>
              </div>
            </div>

            <div className="mt-6">
              {templatesQuery.isLoading && <CardSkeletonGrid />}
              {templatesQuery.isError && <ErrorBanner error={templatesQuery.error} />}

              {templatesQuery.data && filtered.length === 0 && sourceTab === 'BUILT_IN' && (
                <EmptyState
                  icon={<GridIcon className="h-5 w-5" />}
                  title={search ? 'No templates match your search.' : `No ${activeCategory.label.toLowerCase()} templates yet.`}
                  {...(search ? {} : { hint: `${activeCategory.label} templates will appear here once they're added.` })}
                />
              )}

              {templatesQuery.data && filtered.length === 0 && sourceTab === 'CUSTOM' && (
                <EmptyState
                  icon={<GridIcon className="h-5 w-5" />}
                  title={search ? 'No templates match your search.' : 'No custom templates yet.'}
                  {...(search
                    ? {}
                    : {
                        hint: 'Upload your own resume design and CareerForge AI will preserve its layout, filling in your real content.',
                        action: (
                          <Button className="!px-5 !py-2.5 !text-sm" onClick={() => setShowUploadWizard(true)}>
                            Upload Template
                          </Button>
                        ),
                      })}
                />
              )}

              {filtered.length > 0 && (
                <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
                  {filtered.map((template) =>
                    template.source === 'BUILT_IN' ? (
                      <TemplateCard
                        key={template.templateId}
                        template={template}
                        generationType={activeCategory.generationType}
                        selected={template.templateId === highlightedTemplateId}
                        onPreview={() => setPreviewTemplate(template)}
                      />
                    ) : (
                      <CustomTemplateCard
                        key={template.templateId}
                        template={template}
                        selected={template.templateId === highlightedTemplateId}
                        onUse={template.type === 'RESUME' ? () => setGenerateTemplate(template) : null}
                        onEditMapping={() => setEditMappingTemplate(template)}
                        onPreview={() => setPreviewTemplate(template)}
                      />
                    ),
                  )}
                </div>
              )}
            </div>
          </Card>

          {showUploadWizard && (
            <UploadTemplateWizard defaultType={category} onClose={() => setShowUploadWizard(false)} />
          )}
          {editMappingTemplate && (
            <EditMappingModal template={editMappingTemplate} onClose={() => setEditMappingTemplate(null)} />
          )}
          {generateTemplate && (
            <GenerateFromCustomTemplateModal template={generateTemplate} onClose={() => setGenerateTemplate(null)} />
          )}
          {previewTemplate && (
            <TemplatePreviewModal
              template={previewTemplate}
              onClose={() => setPreviewTemplate(null)}
              primaryAction={previewPrimaryAction}
            />
          )}
        </>
      )}
    </DashboardShell>
  );
}
