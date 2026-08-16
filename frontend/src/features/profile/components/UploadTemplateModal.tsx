import { useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { describeError } from '@/components/ui/ErrorBanner';
import { Select } from '@/components/ui/Select';
import { TextField } from '@/components/ui/TextField';
import { showToast } from '@/components/ui/toast';
import { XIcon } from '@/features/dashboard/icons';
import { uploadTemplate, type TemplateDocumentType, type TemplateResponse } from '@/services/templateApi';

const ACCEPTED_EXTENSIONS = ['.pdf', '.docx'];

function hasAcceptedExtension(filename: string): boolean {
  const lower = filename.toLowerCase();
  return ACCEPTED_EXTENSIONS.some((ext) => lower.endsWith(ext));
}

/**
 * "Add Template" — the one place a user ever uploads a Resume/Cover Letter file. Uploaded once
 * here, then only ever selected (never re-uploaded) at JD-optimization handoff time (ADR-034).
 */
export function UploadTemplateModal({
  onClose,
  onUploaded,
}: {
  onClose: () => void;
  onUploaded: (template: TemplateResponse) => void;
}) {
  const [file, setFile] = useState<File | null>(null);
  const [name, setName] = useState('');
  const [documentType, setDocumentType] = useState<TemplateDocumentType>('RESUME');
  const [pickerError, setPickerError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const upload = useMutation({
    mutationFn: () => uploadTemplate({ file: file as File, name: name.trim() || undefined, documentType }),
    onSuccess: (template) => {
      showToast(`"${template.name}" saved to My Templates.`);
      onUploaded(template);
    },
  });

  const pickFile = (selected: File | null) => {
    setPickerError(null);
    if (!selected) {
      setFile(null);
      return;
    }
    if (!hasAcceptedExtension(selected.name)) {
      setPickerError('Only PDF (.pdf) or Word (.docx) files are supported.');
      setFile(null);
      return;
    }
    if (selected.size > 5 * 1024 * 1024) {
      setPickerError('That file is larger than the 5 MB limit.');
      setFile(null);
      return;
    }
    setFile(selected);
    if (!name.trim()) {
      setName(selected.name.replace(/\.(pdf|docx)$/i, ''));
    }
  };

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!file) {
      setPickerError('Choose a PDF or DOCX file first.');
      return;
    }
    upload.mutate();
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-void/80 p-4"
      role="dialog"
      aria-modal="true"
      aria-label="Add template"
      onClick={onClose}
    >
      <form
        onSubmit={submit}
        onClick={(event) => event.stopPropagation()}
        className="w-full max-w-md rounded-2xl border border-border bg-surface p-5 sm:p-6"
      >
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 className="text-lg font-semibold text-ink">Add template</h2>
            <p className="mt-1 text-sm text-ink-muted">
              Upload a Resume or Cover Letter file once — you'll reuse it for every generation.
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="rounded-full p-1.5 text-ink-faint transition-colors hover:text-ink"
          >
            <XIcon className="h-5 w-5" />
          </button>
        </div>

        <div className="mt-5 space-y-4">
          <label className="block" htmlFor="template-file">
            <span className="text-sm font-medium text-ink">File</span>
            <input
              ref={fileInputRef}
              id="template-file"
              type="file"
              accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
              onChange={(event) => pickFile(event.target.files?.[0] ?? null)}
              className="mt-2 block w-full text-sm text-ink-muted file:mr-3 file:rounded-full file:border-0 file:bg-surface-2 file:px-4 file:py-2 file:text-sm file:font-medium file:text-ink hover:file:bg-border"
            />
            <span className="mt-1.5 block text-xs text-ink-faint">PDF or DOCX, up to 5 MB.</span>
            {pickerError && <span className="mt-1.5 block text-xs text-rose">{pickerError}</span>}
          </label>

          <TextField
            label="Template name"
            name="name"
            placeholder="e.g. My Professional Resume"
            value={name}
            onChange={(event) => setName(event.target.value)}
            maxLength={120}
          />

          <Select
            label="Type"
            name="documentType"
            value={documentType}
            onChange={(event) => setDocumentType(event.target.value as TemplateDocumentType)}
          >
            <option value="RESUME">Resume</option>
            <option value="COVER_LETTER">Cover Letter</option>
            <option value="BOTH">Both</option>
          </Select>

          {upload.isError && <p className="text-sm text-rose">{describeError(upload.error)}</p>}
        </div>

        <div className="mt-6 flex flex-col gap-2 sm:flex-row">
          <Button type="submit" variant="primary" loading={upload.isPending} disabled={!file}>
            Save template
          </Button>
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
        </div>
      </form>
    </div>
  );
}
