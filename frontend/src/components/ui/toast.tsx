import { useSyncExternalStore } from 'react';

/**
 * Minimal global toast — a module-level store + useSyncExternalStore, no context provider
 * and no new dependency. `showToast()` can be called from anywhere (a form's onSubmit, a
 * wizard step's onClick); <ToastViewport/> (mounted once in App.tsx) is the only thing that
 * subscribes and renders.
 */
interface ToastItem {
  id: number;
  message: string;
}

let toasts: ToastItem[] = [];
let nextId = 0;
const listeners = new Set<() => void>();

function emit() {
  for (const listener of listeners) listener();
}

export function showToast(message: string, durationMs = 2600) {
  const id = ++nextId;
  toasts = [...toasts, { id, message }];
  emit();
  setTimeout(() => {
    toasts = toasts.filter((t) => t.id !== id);
    emit();
  }, durationMs);
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function getSnapshot() {
  return toasts;
}

export function ToastViewport() {
  const items = useSyncExternalStore(subscribe, getSnapshot);

  if (items.length === 0) return null;

  return (
    <div
      aria-live="polite"
      className="pointer-events-none fixed inset-x-0 bottom-6 z-50 flex flex-col items-center gap-2 px-4"
    >
      {items.map((item) => (
        <div
          key={item.id}
          role="status"
          className="animate-toast-in pointer-events-auto flex items-center gap-2 rounded-full border border-mint/30 bg-surface px-4 py-2.5 text-sm font-medium text-ink shadow-lg shadow-black/40"
        >
          <svg viewBox="0 0 20 20" fill="currentColor" className="h-4 w-4 shrink-0 text-mint" aria-hidden="true">
            <path
              fillRule="evenodd"
              d="M16.7 5.3a1 1 0 0 1 0 1.4l-7.5 7.5a1 1 0 0 1-1.4 0l-3.5-3.5a1 1 0 1 1 1.4-1.4l2.8 2.8 6.8-6.8a1 1 0 0 1 1.4 0Z"
              clipRule="evenodd"
            />
          </svg>
          {item.message}
        </div>
      ))}
    </div>
  );
}
