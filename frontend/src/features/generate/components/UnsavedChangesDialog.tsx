import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';

/**
 * Three-way version of `ConfirmDialog` (redesign spec — Edit JD's unsaved-changes guard) —
 * shown whenever the user tries to leave/cancel/confirm while the job description textarea has
 * an unsaved edit. Kept as its own small component rather than extending the generic
 * `ConfirmDialog` (used elsewhere for plain confirm/cancel) with a third button it would never
 * otherwise need.
 */
export function UnsavedChangesDialog({
  onContinueEditing,
  onDiscard,
  onSave,
  saving,
  error,
}: {
  onContinueEditing: () => void;
  onDiscard: () => void;
  onSave: () => void;
  saving?: boolean;
  error?: unknown;
}) {
  const isSaving = saving ?? false;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-void/70 backdrop-blur-sm animate-toast-in" onClick={onContinueEditing} />
      <div className="animate-card-in relative w-full max-w-sm rounded-2xl border border-border bg-surface p-6 shadow-2xl">
        <h2 className="text-base font-semibold text-ink">Unsaved changes</h2>
        <p className="mt-2 text-sm text-ink-muted">
          You've edited the job description but haven't saved it yet. What would you like to do?
        </p>

        {error !== undefined && error !== null && (
          <div className="mt-3">
            <ErrorBanner error={error} />
          </div>
        )}

        <div className="mt-6 flex flex-col gap-2.5">
          <Button type="button" className="w-full !py-2.5 !text-sm" loading={isSaving} onClick={onSave}>
            Save Changes
          </Button>
          <Button type="button" variant="secondary" className="w-full !py-2.5 !text-sm" disabled={isSaving} onClick={onDiscard}>
            Discard Changes
          </Button>
          <button
            type="button"
            disabled={isSaving}
            onClick={onContinueEditing}
            className="w-full py-1.5 text-center text-xs font-medium text-ink-muted transition-colors hover:text-ink disabled:opacity-50"
          >
            Continue Editing
          </button>
        </div>
      </div>
    </div>
  );
}
