import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import type { Template } from '@/services/templateApi';
import { DocumentPreview } from './DocumentPreview';

/**
 * A built-in template's card on the Templates page (redesign brief &sect;5-7/9) — the real,
 * rendered document is the dominant visual (see `DocumentPreview`), not a name/filename with
 * metadata bolted on. Custom uploads get the equivalent treatment inside `CustomTemplateCard`,
 * which reuses the same `DocumentPreview`/`TemplatePreviewModal` rather than a second
 * implementation of either.
 */
export function TemplateCard({
  template,
  generationType,
  selected = false,
  onPreview,
}: {
  template: Template;
  generationType: string;
  selected?: boolean;
  onPreview: () => void;
}) {
  return (
    <div
      className={`group flex flex-col overflow-hidden rounded-2xl border bg-surface transition-all hover:-translate-y-0.5 hover:shadow-xl hover:shadow-black/20 ${
        selected ? 'border-ember-soft ring-2 ring-ember-soft/40' : 'border-border-strong hover:border-ink-muted'
      }`}
    >
      <div
        role="button"
        tabIndex={0}
        aria-label={`Preview ${template.name}`}
        onClick={onPreview}
        onKeyDown={(event) => {
          if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            onPreview();
          }
        }}
        className="relative aspect-[8.5/11] w-full cursor-pointer overflow-hidden bg-white outline-none focus-visible:ring-2 focus-visible:ring-ember-soft"
      >
        {selected && (
          <span className="absolute left-2.5 top-2.5 z-10 inline-flex items-center gap-1 rounded-full bg-ember-soft px-2.5 py-1 text-[11px] font-semibold text-void shadow">
            ✓ Selected
          </span>
        )}
        <DocumentPreview templateId={template.templateId} renderWidth={640} className="h-full w-full" />
        <div className="pointer-events-none absolute inset-0 flex scale-[1.02] items-end justify-center bg-linear-to-t from-black/70 via-black/10 to-transparent opacity-0 transition-all duration-200 group-hover:scale-100 group-hover:opacity-100">
          <span className="mb-4 inline-flex items-center gap-1.5 rounded-full bg-white px-4 py-2 text-xs font-semibold text-void shadow-lg">
            View full preview
          </span>
        </div>
      </div>

      <div className="flex flex-1 flex-col gap-3 p-4">
        <div className="flex items-center justify-between gap-2">
          <h3 className="truncate text-sm font-semibold text-ink">{template.name}</h3>
          {template.atsSafe && (
            <span className="shrink-0 rounded-full bg-mint/10 px-2 py-0.5 text-[11px] font-medium text-mint">ATS-safe</span>
          )}
        </div>
        <p className="text-xs text-ink-faint">{formatType(template.type)} · Built-in</p>
        {template.description && <p className="text-xs leading-relaxed text-ink-muted">{template.description}</p>}

        <div className="mt-auto flex gap-2 pt-1">
          <Button type="button" variant="secondary" className="flex-1 !py-2 !text-xs" onClick={onPreview}>
            Preview
          </Button>
          {selected ? (
            <span className="flex flex-1 items-center justify-center gap-1.5 rounded-full bg-mint/10 py-2 text-xs font-semibold text-mint">
              ✓ Using this template
            </span>
          ) : (
            <Link to={`/generate/job?type=${generationType}&templateId=${template.templateId}`} className="flex-1">
              <Button className="w-full !py-2 !text-xs">Use this template</Button>
            </Link>
          )}
        </div>
      </div>
    </div>
  );
}

function formatType(type: string): string {
  return type
    .toLowerCase()
    .split('_')
    .map((word) => word[0]?.toUpperCase() + word.slice(1))
    .join(' ');
}
