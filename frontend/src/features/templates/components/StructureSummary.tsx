import type { TemplateStructure } from '@/services/templateApi';

const TWIPS_PER_INCH = 1440;

function inches(twips: number | null): string | null {
  return twips === null ? null : `${(twips / TWIPS_PER_INCH).toFixed(2)}"`;
}

/** Renders the real structural facts document-service's analyzer extracted from an uploaded
 *  .docx — the honest substitute for a rendered visual preview (see TemplatesPage's own
 *  comment on why a true WYSIWYG DOCX preview isn't built: no DOCX→image/PDF converter exists
 *  in this stack). Every value here came straight off the file's own OOXML; nothing is
 *  invented. Shared by the upload wizard's review step and the "Preview" action on an
 *  already-saved template. */
export function StructureSummary({ structure }: { structure: TemplateStructure }) {
  const pageSize = inches(structure.pageWidthTwips) && inches(structure.pageHeightTwips)
    ? `${inches(structure.pageWidthTwips)} × ${inches(structure.pageHeightTwips)}`
    : 'Not detected';
  const margins = [
    inches(structure.marginTopTwips),
    inches(structure.marginRightTwips),
    inches(structure.marginBottomTwips),
    inches(structure.marginLeftTwips),
  ];
  const marginsLabel = margins.every((m) => m !== null) ? `${margins.join(' / ')} (T/R/B/L)` : 'Not detected';

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
        <Fact label="Page size" value={pageSize} />
        <Fact label="Margins" value={marginsLabel} />
        <Fact label="Columns" value={String(structure.columnCount)} />
        <Fact label="Paragraphs" value={String(structure.paragraphCount)} />
        <Fact label="Tables" value={String(structure.tableCount)} />
        <Fact label="Header / Footer" value={`${structure.hasHeader ? 'Yes' : 'No'} / ${structure.hasFooter ? 'Yes' : 'No'}`} />
      </div>

      {structure.fontsUsed.length > 0 && (
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-ink-faint">Fonts</p>
          <p className="mt-1 text-sm text-ink-muted">{structure.fontsUsed.join(', ')}</p>
        </div>
      )}

      {structure.fontSizesPt.length > 0 && (
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-ink-faint">Font sizes</p>
          <p className="mt-1 text-sm text-ink-muted">{structure.fontSizesPt.map((s) => `${s}pt`).join(', ')}</p>
        </div>
      )}

      {structure.colorsUsedHex.length > 0 && (
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-ink-faint">Colors</p>
          <div className="mt-1.5 flex flex-wrap gap-2">
            {structure.colorsUsedHex.map((hex) => (
              <span key={hex} className="flex items-center gap-1.5 rounded-full border border-border-strong px-2 py-1 text-xs text-ink-muted">
                <span className="h-3 w-3 rounded-full border border-border" style={{ backgroundColor: `#${hex}` }} aria-hidden="true" />
                #{hex}
              </span>
            ))}
          </div>
        </div>
      )}

      {structure.alignmentsUsed.length > 0 && (
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-ink-faint">Alignment</p>
          <p className="mt-1 text-sm text-ink-muted">{structure.alignmentsUsed.join(', ')}</p>
        </div>
      )}

      {structure.headingsFound.length > 0 && (
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-ink-faint">Headings found</p>
          <ul className="mt-1.5 space-y-1">
            {structure.headingsFound.map((h, i) => (
              <li key={i} className="text-sm text-ink-muted">• {h}</li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-border bg-surface-2/50 px-3 py-2.5">
      <p className="text-[11px] uppercase tracking-wide text-ink-faint">{label}</p>
      <p className="mt-0.5 text-sm font-medium text-ink">{value}</p>
    </div>
  );
}
