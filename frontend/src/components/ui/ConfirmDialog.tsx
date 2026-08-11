import { Button } from './Button';

/** Generic destructive-action confirmation modal — first consumer is custom-template delete,
 *  but written to be reusable anywhere a "are you sure?" step is needed. Backdrop click and
 *  Escape both cancel, matching every other dismissible overlay in this app (see UserMenu,
 *  DashboardSidebar's mobile drawer). */
export function ConfirmDialog({
  title,
  message,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  danger = false,
  loading = false,
  onConfirm,
  onCancel,
}: {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  danger?: boolean;
  loading?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-void/70 backdrop-blur-sm animate-toast-in" onClick={onCancel} />
      <div className="animate-card-in relative w-full max-w-sm rounded-2xl border border-border bg-surface p-6 shadow-2xl">
        <h2 className="text-base font-semibold text-ink">{title}</h2>
        <p className="mt-2 text-sm text-ink-muted">{message}</p>
        <div className="mt-6 flex justify-end gap-3">
          <Button type="button" variant="secondary" className="!px-4 !py-2 !text-sm" onClick={onCancel}>
            {cancelLabel}
          </Button>
          <Button
            type="button"
            className={`!px-4 !py-2 !text-sm ${danger ? '!bg-none !bg-rose hover:!brightness-110' : ''}`}
            loading={loading}
            onClick={onConfirm}
          >
            {confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  );
}
