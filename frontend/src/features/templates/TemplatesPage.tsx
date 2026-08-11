import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
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
import { TemplatePreview } from '@/features/generate/components/TemplatePreview';
import { listTemplates, type Template, type TemplateDocumentType } from '@/services/templateApi';
import { CustomTemplateCard } from './components/CustomTemplateCard';
import { EditMappingModal } from './components/EditMappingModal';
import { GenerateFromCustomTemplateModal } from './components/GenerateFromCustomTemplateModal';
import { StructureSummary } from './components/StructureSummary';
import { UploadTemplateWizard } from './components/UploadTemplateWizard';

type SourceTab = 'BUILT_IN' | 'CUSTOM';
type SortBy = 'newest' | 'name' | 'type';

const CATEGORIES: { id: TemplateDocumentType; label: string; generationType: string }[] = [
  { id: 'RESUME', label: 'Resume', generationType: 'RESUME_ONLY' },
  { id: 'COVER_LETTER', label: 'Cover Letter', generationType: 'COVER_LETTER_ONLY' },
  { id: 'EMAIL', label: 'Email', generationType: 'EMAIL_ONLY' },
];

const SORT_OPTIONS: { id: SortBy; label: string }[] = [
  { id: 'newest', label: 'Newest' },
  { id: 'name', label: 'Name' },
  { id: 'type', label: 'Type' },
];

function sortTemplates(items: Template[], sortBy: SortBy): Template[] {
  const copy = [...items];
  if (sortBy === 'name') return copy.sort((a, b) => a.name.localeCompare(b.name));
  if (sortBy === 'type') return copy.sort((a, b) => a.type.localeCompare(b.type));
  return copy.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
}

function BuiltInTemplateCard({ template, generationType, onPreview }: { template: Template; generationType: string; onPreview: () => void }) {
  return (
    <div className="flex flex-col gap-3 rounded-2xl border border-border-strong p-4">
      <TemplatePreview previewKey={template.previewKey} />
      <div>
        <div className="flex items-center justify-between gap-2">
          <h3 className="text-sm font-semibold text-ink">{template.name}</h3>
          {template.atsSafe && (
            <span className="shrink-0 rounded-full bg-mint/10 px-2 py-0.5 text-[11px] font-medium text-mint">ATS-safe</span>
          )}
        </div>
        <p className="mt-1 text-xs leading-relaxed text-ink-muted">{template.description}</p>
        <div className="mt-3 flex gap-2">
          <Button type="button" variant="secondary" className="!px-3 !py-2 !text-xs" onClick={onPreview}>
            Preview
          </Button>
          <Link to={`/generate/job?type=${generationType}&templateId=${template.templateId}`} className="flex-1">
            <Button variant="secondary" className="w-full !py-2 !text-xs">
              Use this template
            </Button>
          </Link>
        </div>
      </div>
    </div>
  );
}

/**
 * The complete template library — built-in (resume-service's seeded catalogue) and, new in
 * this slice, a user's own uploaded custom templates, side by side. Both come back from the
 * same real `listTemplates` call (resume-service now scopes custom rows to the caller — see
 * TemplateService#list) and are split into the [Built-in Templates] / [My Templates] tabs
 * purely client-side by `source`.
 */
export function TemplatesPage() {
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

            <div className="mt-5">
              {templatesQuery.isLoading && <p className="text-sm text-ink-faint">Loading…</p>}
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
                  title={search ? 'No templates match your search.' : "You haven't uploaded a template yet."}
                  {...(search
                    ? {}
                    : {
                        hint: 'Upload your own resume, cover letter or email design and CareerForge will fill it with your real content.',
                        action: (
                          <Button className="!px-5 !py-2.5 !text-sm" onClick={() => setShowUploadWizard(true)}>
                            Upload custom template
                          </Button>
                        ),
                      })}
                />
              )}

              {filtered.length > 0 && (
                <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                  {filtered.map((template) =>
                    template.source === 'BUILT_IN' ? (
                      <BuiltInTemplateCard
                        key={template.templateId}
                        template={template}
                        generationType={activeCategory.generationType}
                        onPreview={() => setPreviewTemplate(template)}
                      />
                    ) : (
                      <CustomTemplateCard
                        key={template.templateId}
                        template={template}
                        onUse={template.type === 'RESUME' ? () => setGenerateTemplate(template) : null}
                        onEditMapping={() => setEditMappingTemplate(template)}
                      />
                    ),
                  )}
                </div>
              )}
            </div>
          </Card>

          {showUploadWizard && (
            <UploadTemplateWizard
              defaultType={category}
              onClose={() => setShowUploadWizard(false)}
            />
          )}
          {editMappingTemplate && (
            <EditMappingModal template={editMappingTemplate} onClose={() => setEditMappingTemplate(null)} />
          )}
          {generateTemplate && (
            <GenerateFromCustomTemplateModal template={generateTemplate} onClose={() => setGenerateTemplate(null)} />
          )}
          {previewTemplate && (
            <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
              <div className="absolute inset-0 bg-void/70 backdrop-blur-sm animate-toast-in" onClick={() => setPreviewTemplate(null)} />
              <div className="animate-card-in relative w-full max-w-md overflow-hidden rounded-2xl border border-border bg-surface p-6 shadow-2xl">
                <div className="flex items-center justify-between">
                  <h2 className="text-base font-semibold text-ink">{previewTemplate.name}</h2>
                  <button
                    type="button"
                    onClick={() => setPreviewTemplate(null)}
                    aria-label="Close"
                    className="flex h-8 w-8 items-center justify-center rounded-lg text-ink-muted hover:text-ink"
                  >
                    ✕
                  </button>
                </div>
                <div className="mt-4">
                  <TemplatePreview previewKey={previewTemplate.previewKey} />
                </div>
                <p className="mt-4 text-sm text-ink-muted">{previewTemplate.description}</p>
                {previewTemplate.structure && (
                  <div className="mt-4">
                    <StructureSummary structure={previewTemplate.structure} />
                  </div>
                )}
              </div>
            </div>
          )}
        </>
      )}
    </DashboardShell>
  );
}

