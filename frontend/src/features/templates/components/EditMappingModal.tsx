import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { updateTemplateMapping, type Template } from '@/services/templateApi';
import { FieldMappingEditor } from './FieldMappingEditor';

/** Re-opens the same mapping editor the upload wizard uses, for a template that's already
 *  saved — point 4/7 of the feature spec ("Edit Mapping" action on a saved custom template). */
export function EditMappingModal({ template, onClose }: { template: Template; onClose: () => void }) {
  const [mapping, setMapping] = useState<Record<string, string>>(template.fieldMappings ?? {});
  const queryClient = useQueryClient();

  const saveMutation = useMutation({
    mutationFn: () => updateTemplateMapping(template.templateId, mapping),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['templates'] });
      onClose();
    },
  });

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-void/70 backdrop-blur-sm animate-toast-in" onClick={onClose} />
      <div className="animate-card-in relative flex max-h-[85vh] w-full max-w-2xl flex-col overflow-hidden rounded-2xl border border-border bg-surface shadow-2xl">
        <div className="flex items-center justify-between border-b border-border px-6 py-4">
          <div>
            <h2 className="text-base font-semibold text-ink">Edit mapping — {template.name}</h2>
            <p className="mt-0.5 text-xs text-ink-faint">Change which profile field each placeholder pulls from.</p>
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
          <FieldMappingEditor detectedFields={template.detectedFields ?? []} mapping={mapping} onChange={setMapping} />
          {saveMutation.isError && <div className="mt-4"><ErrorBanner error={saveMutation.error} /></div>}
        </div>
        <div className="flex justify-end gap-3 border-t border-border px-6 py-4">
          <Button type="button" variant="secondary" className="!px-4 !py-2 !text-sm" onClick={onClose}>
            Cancel
          </Button>
          <Button type="button" className="!px-5 !py-2 !text-sm" loading={saveMutation.isPending} onClick={() => saveMutation.mutate()}>
            Save mapping
          </Button>
        </div>
      </div>
    </div>
  );
}
