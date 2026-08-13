import { useRef, useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Select } from '@/components/ui/Select';
import { TextField } from '@/components/ui/TextField';
import {
  PROFILE_FIELDS,
  updateTemplateMapping,
  uploadCustomTemplate,
  type Template,
  type TemplateDocumentType,
} from '@/services/templateApi';
import { FieldMappingEditor } from './FieldMappingEditor';
import { StructureSummary } from './StructureSummary';

const MAX_SIZE_BYTES = 5 * 1024 * 1024;

const TYPE_OPTIONS: { value: TemplateDocumentType; label: string }[] = [
  { value: 'RESUME', label: 'Resume' },
  { value: 'COVER_LETTER', label: 'Cover Letter' },
  { value: 'EMAIL', label: 'Email' },
];

type Step = 'file' | 'details' | 'mapping' | 'preview';

const STEP_LABELS: { id: Step; label: string }[] = [
  { id: 'file', label: 'Choose file' },
  { id: 'details', label: 'Details' },
  { id: 'mapping', label: 'Map fields' },
  { id: 'preview', label: 'Preview & save' },
];

function fileNameWithoutExtension(name: string): string {
  const dot = name.lastIndexOf('.');
  return dot > 0 ? name.slice(0, dot) : name;
}

/**
 * Upload → Analyze → Map fields → Preview → Save — the flow the feature spec calls for.
 * "Analyze" isn't a separate step here: `uploadCustomTemplate` does the upload and the real
 * structural analysis in one round trip (document-service returns the extracted structure and
 * detected placeholders straight from that call), so the wizard has real data to show the
 * moment the upload succeeds rather than a second loading step for nothing.
 */
export function UploadTemplateWizard({
  defaultType,
  onClose,
}: {
  defaultType: TemplateDocumentType;
  onClose: (savedTemplate: Template | null) => void;
}) {
  const [step, setStep] = useState<Step>('file');
  const [file, setFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [type, setType] = useState<TemplateDocumentType>(defaultType);
  const [name, setName] = useState('');
  const [uploaded, setUploaded] = useState<Template | null>(null);
  const [mapping, setMapping] = useState<Record<string, string>>({});
  const fileInputRef = useRef<HTMLInputElement>(null);

  const uploadMutation = useMutation({
    mutationFn: () => uploadCustomTemplate(file!, type, name.trim()),
    onSuccess: (template) => {
      setUploaded(template);
      setMapping(template.fieldMappings ?? {});
      setStep('mapping');
    },
  });

  const saveMappingMutation = useMutation({
    mutationFn: () => updateTemplateMapping(uploaded!.templateId, mapping),
    onSuccess: (template) => onClose(template),
  });

  const handleFileChange = (selected: File | null) => {
    setFileError(null);
    if (!selected) {
      setFile(null);
      return;
    }
    const lower = selected.name.toLowerCase();
    if (!lower.endsWith('.docx') && !lower.endsWith('.pdf')) {
      setFileError('Only Word (.docx) or PDF (.pdf) files are supported.');
      setFile(null);
      return;
    }
    if (selected.size > MAX_SIZE_BYTES) {
      setFileError('This file is larger than 5 MB — choose a smaller one.');
      setFile(null);
      return;
    }
    if (selected.size === 0) {
      setFileError('This file is empty.');
      setFile(null);
      return;
    }
    setFile(selected);
    setName((current) => current || fileNameWithoutExtension(selected.name));
  };

  const stepIndex = STEP_LABELS.findIndex((s) => s.id === step);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-void/70 backdrop-blur-sm animate-toast-in" onClick={() => onClose(uploaded)} />
      <div className="animate-card-in relative flex max-h-[90vh] w-full max-w-2xl flex-col overflow-hidden rounded-2xl border border-border bg-surface shadow-2xl">
        <div className="flex items-center justify-between border-b border-border px-6 py-4">
          <div>
            <h2 className="text-base font-semibold text-ink">Upload custom template</h2>
            <p className="mt-0.5 text-xs text-ink-faint">
              Step {stepIndex + 1} of {STEP_LABELS.length}: {STEP_LABELS[stepIndex]!.label}
            </p>
          </div>
          <button
            type="button"
            onClick={() => onClose(uploaded)}
            aria-label="Close"
            className="flex h-8 w-8 items-center justify-center rounded-lg text-ink-muted hover:text-ink"
          >
            ✕
          </button>
        </div>

        <div className="flex-1 overflow-y-auto px-6 py-5">
          {step === 'file' && (
            <div className="space-y-4">
              <p className="text-sm text-ink-muted">
                Upload a Word (.docx) or PDF resume, cover letter or email template. Add placeholders like{' '}
                <code className="rounded bg-surface-2 px-1 py-0.5 text-xs">{'{{name}}'}</code> or{' '}
                <code className="rounded bg-surface-2 px-1 py-0.5 text-xs">{'{{summary}}'}</code> anywhere you want
                your real content to appear — CareerForge fills them in without touching the rest of your design.
                For a PDF template, placeholders must be real, selectable text (not part of a scanned image).
              </p>
              <p className="text-xs text-ink-faint">Supported formats: DOCX, PDF</p>
              <label className="flex cursor-pointer flex-col items-center justify-center gap-2 rounded-2xl border-2 border-dashed border-border-strong px-6 py-10 text-center transition-colors hover:border-ink-muted">
                <input
                  ref={fileInputRef}
                  type="file"
                  accept=".docx,.pdf"
                  className="sr-only"
                  onChange={(e) => handleFileChange(e.target.files?.[0] ?? null)}
                />
                <span className="text-sm font-medium text-ink">
                  {file ? file.name : 'Click to choose a .docx or .pdf file'}
                </span>
                <span className="text-xs text-ink-faint">
                  {file ? `${(file.size / 1024).toFixed(0)} KB` : 'Word (.docx) or PDF (.pdf), up to 5 MB'}
                </span>
              </label>
              {file && (
                <p className="text-xs text-ink-faint">
                  ✓ {file.name}
                  <span className="ml-2 rounded-full border border-border-strong px-2 py-0.5 text-[11px] uppercase text-ink-muted">
                    {file.name.toLowerCase().endsWith('.pdf') ? 'PDF' : 'DOCX'}
                  </span>
                </p>
              )}
              {fileError && <ErrorBanner error={new Error(fileError)} />}
            </div>
          )}

          {step === 'details' && (
            <div className="space-y-4">
              <Select
                label="Template type"
                value={type}
                onChange={(e) => setType(e.target.value as TemplateDocumentType)}
              >
                {TYPE_OPTIONS.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </Select>
              <TextField
                label="Template name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="e.g. My Resume Template"
                maxLength={120}
              />
              {uploadMutation.isError && <ErrorBanner error={uploadMutation.error} />}
            </div>
          )}

          {step === 'mapping' && uploaded && (
            <div className="space-y-4">
              <p className="text-xs font-medium text-mint">
                ✓ {uploaded.structure?.sourceType === 'PDF' ? 'PDF' : 'DOCX'} template analyzed
              </p>
              <p className="text-sm text-ink-muted">
                CareerForge found {uploaded.detectedFields?.length ?? 0} placeholder
                {(uploaded.detectedFields?.length ?? 0) === 1 ? '' : 's'} in your document. Review the suggested
                mapping below, or change any of them.
              </p>
              <FieldMappingEditor
                detectedFields={uploaded.detectedFields ?? []}
                mapping={mapping}
                onChange={setMapping}
              />
            </div>
          )}

          {step === 'preview' && uploaded && (
            <div className="space-y-5">
              <div>
                <p className="text-xs font-semibold uppercase tracking-wide text-ink-faint">Detected structure</p>
                <div className="mt-2">{uploaded.structure && <StructureSummary structure={uploaded.structure} />}</div>
              </div>
              <div>
                <p className="text-xs font-semibold uppercase tracking-wide text-ink-faint">Final field mapping</p>
                {Object.keys(mapping).length === 0 ? (
                  <p className="mt-2 text-sm text-ink-faint">No fields mapped yet.</p>
                ) : (
                  <ul className="mt-2 space-y-1.5">
                    {Object.entries(mapping).map(([token, fieldKey]) => (
                      <li key={token} className="flex items-center gap-2 text-sm">
                        <code className="rounded bg-surface-2 px-1.5 py-0.5 text-xs text-ink">{`{{${token}}}`}</code>
                        <span className="text-ink-faint">→</span>
                        <span className="text-ink-muted">
                          {PROFILE_FIELDS.find((f) => f.key === fieldKey)?.label ?? fieldKey}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
              {saveMappingMutation.isError && <ErrorBanner error={saveMappingMutation.error} />}
            </div>
          )}
        </div>

        <div className="flex items-center justify-between border-t border-border px-6 py-4">
          <Button
            type="button"
            variant="secondary"
            className="!px-4 !py-2 !text-sm"
            disabled={step === 'file' || uploadMutation.isPending}
            onClick={() => {
              if (step === 'details') setStep('file');
              else if (step === 'mapping') setStep('details');
              else if (step === 'preview') setStep('mapping');
            }}
          >
            Back
          </Button>

          {step === 'file' && (
            <Button type="button" className="!px-5 !py-2 !text-sm" disabled={!file} onClick={() => setStep('details')}>
              Next
            </Button>
          )}
          {step === 'details' && (
            <Button
              type="button"
              className="!px-5 !py-2 !text-sm"
              loading={uploadMutation.isPending}
              disabled={!name.trim() || !file}
              onClick={() => uploadMutation.mutate()}
            >
              Upload &amp; analyze
            </Button>
          )}
          {step === 'mapping' && (
            <Button type="button" className="!px-5 !py-2 !text-sm" onClick={() => setStep('preview')}>
              Next: Preview
            </Button>
          )}
          {step === 'preview' && (
            <Button
              type="button"
              className="!px-5 !py-2 !text-sm"
              loading={saveMappingMutation.isPending}
              onClick={() => saveMappingMutation.mutate()}
            >
              Save template
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}
