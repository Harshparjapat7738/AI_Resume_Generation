/**
 * Real, honest previews — not stock images. Each mockup below reflects the actual structural
 * definition of the matching built-in template (see resume-service's `TemplateSeeder`):
 * column count, header treatment, section-heading style. There is no thumbnail-rendering
 * pipeline yet (ADR-016), so this is the frontend's local rendering of that same structure,
 * not a claim about a file that doesn't exist.
 */
import type { ReactElement } from 'react';

function Lines({ count, width = 'w-full' }: { count: number; width?: string }) {
  return (
    <div className="space-y-1">
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className={`h-1 rounded-full bg-border ${i === count - 1 ? 'w-2/3' : width}`} />
      ))}
    </div>
  );
}

function SectionHeading({ underline = true, accent = false }: { underline?: boolean; accent?: boolean }) {
  return (
    <div
      className={`h-1.5 w-1/3 rounded-full ${accent ? 'bg-ember-soft' : 'bg-ink-faint'} ${
        underline ? 'border-b border-border pb-1' : ''
      }`}
    />
  );
}

function ClassicPreview() {
  return (
    <div className="flex h-full flex-col items-center gap-3 px-4 py-4 text-center">
      <div className="h-2 w-1/2 rounded-full bg-ink-muted" />
      <div className="h-1 w-1/3 rounded-full bg-ink-faint" />
      <div className="mt-1 w-full space-y-2.5 text-left">
        <SectionHeading />
        <Lines count={3} />
        <SectionHeading />
        <Lines count={2} />
      </div>
    </div>
  );
}

function ModernAtsPreview() {
  return (
    <div className="flex h-full flex-col gap-3 px-4 py-4">
      <div className="space-y-1">
        <div className="h-2 w-2/5 rounded-full bg-ink-muted" />
        <div className="h-1 w-1/4 rounded-full bg-ink-faint" />
      </div>
      <div className="mt-1 space-y-2.5">
        <SectionHeading accent />
        <Lines count={3} />
        <SectionHeading accent />
        <Lines count={2} />
      </div>
    </div>
  );
}

function ProfessionalPreview() {
  return (
    <div className="flex h-full flex-col gap-3 px-0 py-0">
      <div className="space-y-1 bg-ember-soft/20 px-4 py-3">
        <div className="h-2 w-2/5 rounded-full bg-ink-muted" />
        <div className="h-1 w-1/4 rounded-full bg-ink-faint" />
      </div>
      <div className="space-y-2.5 px-4 pb-4">
        <SectionHeading />
        <Lines count={3} />
        <SectionHeading />
        <Lines count={2} />
      </div>
    </div>
  );
}

const previews: Record<string, { render: () => ReactElement; alt: string }> = {
  classic: {
    render: ClassicPreview,
    alt: 'Classic template: centered header, underlined section headings, single column.',
  },
  'modern-ats': {
    render: ModernAtsPreview,
    alt: 'Modern ATS template: left-aligned header, accent rule under each section heading, single column.',
  },
  professional: {
    render: ProfessionalPreview,
    alt: 'Professional template: color band behind the header, single-column body.',
  },
};

export function TemplatePreview({ previewKey }: { previewKey: string }) {
  const entry = previews[previewKey];
  if (!entry) {
    return (
      <div className="flex h-40 items-center justify-center rounded-xl border border-border bg-void text-xs text-ink-faint">
        Preview unavailable
      </div>
    );
  }
  const Render = entry.render;
  return (
    <div
      role="img"
      aria-label={entry.alt}
      className="h-40 overflow-hidden rounded-xl border border-border bg-void"
    >
      <Render />
    </div>
  );
}
