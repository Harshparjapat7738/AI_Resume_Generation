import { useState } from 'react';
import type { FormEvent } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { describeError } from '@/components/ui/ErrorBanner';
import { TextField } from '@/components/ui/TextField';
import { showToast } from '@/components/ui/toast';
import { renameTemplate, type TemplateResponse } from '@/services/templateApi';

export function RenameTemplateModal({
  template,
  onClose,
  onRenamed,
}: {
  template: TemplateResponse;
  onClose: () => void;
  onRenamed: (template: TemplateResponse) => void;
}) {
  const [name, setName] = useState(template.name);

  const rename = useMutation({
    mutationFn: () => renameTemplate(template.id, name.trim()),
    onSuccess: (updated) => {
      showToast('Template renamed.');
      onRenamed(updated);
    },
  });

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!name.trim()) return;
    rename.mutate();
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-void/80 p-4"
      role="dialog"
      aria-modal="true"
      aria-label="Rename template"
      onClick={onClose}
    >
      <form
        onSubmit={submit}
        onClick={(event) => event.stopPropagation()}
        className="w-full max-w-sm rounded-2xl border border-border bg-surface p-5 sm:p-6"
      >
        <h2 className="text-lg font-semibold text-ink">Rename template</h2>
        <div className="mt-4">
          <TextField
            label="Template name"
            name="name"
            autoFocus
            value={name}
            onChange={(event) => setName(event.target.value)}
            maxLength={120}
          />
          {rename.isError && <p className="mt-2 text-sm text-rose">{describeError(rename.error)}</p>}
        </div>
        <div className="mt-6 flex flex-col gap-2 sm:flex-row">
          <Button type="submit" variant="primary" loading={rename.isPending} disabled={!name.trim()}>
            Save
          </Button>
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
        </div>
      </form>
    </div>
  );
}
