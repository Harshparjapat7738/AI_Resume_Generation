import { Select } from '@/components/ui/Select';
import { PROFILE_FIELDS, type DetectedField } from '@/services/templateApi';

const NOT_MAPPED = '';

/**
 * Detected placeholder → profile field, editable. Every row is real: `detectedFields` came
 * straight from document-service's analysis of the actual uploaded file (see
 * DocxStructureAnalyzer) — a token that isn't in the document isn't listed, and nothing here
 * is auto-applied without the owner reviewing it first (the analyzer's guess is just the
 * Select's initial value, exactly like every other field here).
 */
export function FieldMappingEditor({
  detectedFields,
  mapping,
  onChange,
}: {
  detectedFields: DetectedField[];
  mapping: Record<string, string>;
  onChange: (mapping: Record<string, string>) => void;
}) {
  if (detectedFields.length === 0) {
    return (
      <div className="rounded-2xl border border-dashed border-border px-6 py-8 text-center">
        <p className="text-sm font-medium text-ink-muted">No {'{{placeholders}}'} were found in this document.</p>
        <p className="mt-1.5 text-sm text-ink-faint">
          Add placeholders like <code className="rounded bg-surface-2 px-1 py-0.5 text-xs">{'{{name}}'}</code> or{' '}
          <code className="rounded bg-surface-2 px-1 py-0.5 text-xs">{'{{experience}}'}</code> to your document and
          re-upload it, so CareerForge knows exactly where to place your content.
        </p>
      </div>
    );
  }

  const setField = (token: string, value: string) => {
    const next = { ...mapping };
    if (value === NOT_MAPPED) {
      delete next[token];
    } else {
      next[token] = value;
    }
    onChange(next);
  };

  return (
    <div className="overflow-hidden rounded-2xl border border-border">
      <table className="w-full text-left text-sm">
        <thead>
          <tr className="border-b border-border bg-surface-2/50 text-xs uppercase tracking-wide text-ink-faint">
            <th className="px-4 py-2.5 font-medium">Placeholder</th>
            <th className="px-4 py-2.5 font-medium">Found near</th>
            <th className="px-4 py-2.5 font-medium">Maps to</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-border">
          {detectedFields.map((field) => (
            <tr key={field.token}>
              <td className="px-4 py-3 align-top">
                <code className="rounded bg-surface-2 px-1.5 py-0.5 text-xs text-ink">{`{{${field.token}}}`}</code>
              </td>
              <td className="max-w-xs px-4 py-3 align-top text-xs text-ink-faint">{field.context}</td>
              <td className="px-4 py-3 align-top">
                <Select
                  label=""
                  aria-label={`Map {{${field.token}}} to a profile field`}
                  value={mapping[field.token] ?? NOT_MAPPED}
                  onChange={(e) => setField(field.token, e.target.value)}
                  className="!mt-0 !py-1.5"
                >
                  <option value={NOT_MAPPED}>Not mapped</option>
                  {PROFILE_FIELDS.map((f) => (
                    <option key={f.key} value={f.key}>
                      {f.label}
                    </option>
                  ))}
                </Select>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
