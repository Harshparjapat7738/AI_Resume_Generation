import type { ReactNode } from 'react';

export function EmptyState({
  icon,
  title,
  hint,
  action,
}: {
  icon?: ReactNode;
  title: string;
  hint?: string;
  action?: ReactNode;
}) {
  return (
    <div className="rounded-2xl border border-dashed border-border px-6 py-10 text-center">
      {icon && (
        <span className="mx-auto flex h-11 w-11 items-center justify-center rounded-full bg-surface-2 text-ink-faint">
          {icon}
        </span>
      )}
      <p className={`text-sm font-medium text-ink-muted ${icon ? 'mt-3' : ''}`}>{title}</p>
      {hint && <p className="mx-auto mt-1.5 max-w-sm text-sm text-ink-faint">{hint}</p>}
      {action && <div className="mt-4 flex justify-center">{action}</div>}
    </div>
  );
}
