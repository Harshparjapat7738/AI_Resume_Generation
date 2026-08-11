import type { ReactNode } from 'react';
import { Card } from '@/components/ui/Card';
import { Tag } from './Tag';
import { VerifiedBadge } from './VerifiedBadge';

/** Generic display card for a single profile record (education, project, certification,
 *  achievement). Presentation only — the caller still owns all CRUD state/logic and just
 *  hands this the pieces to render, so it stays a thin, reusable shell rather than another
 *  place business logic could drift into. */
export function RecordCard({
  title,
  meta,
  tags,
  children,
  onEdit,
  onDelete,
  deleting = false,
  removeLabel,
}: {
  title: ReactNode;
  meta?: ReactNode;
  tags?: string[];
  children?: ReactNode;
  onEdit: () => void;
  onDelete: () => void;
  deleting?: boolean;
  /** When set, the delete action renders as a compact "×" icon button (aria-labelled with
   *  this text) instead of the default text "Delete" link — opt-in so existing callers keep
   *  their current look. */
  removeLabel?: string;
}) {
  return (
    <Card className="!p-5 transition-colors hover:border-border-strong">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <p className="text-sm font-medium text-ink">{title}</p>
          {meta && <p className="mt-0.5 text-xs text-ink-faint">{meta}</p>}
          {children}
          {tags && tags.length > 0 && (
            <div className="mt-3 flex flex-wrap gap-1.5">
              {tags.map((tag) => (
                <Tag key={tag}>{tag}</Tag>
              ))}
            </div>
          )}
          <div className="mt-3">
            <VerifiedBadge />
          </div>
        </div>
        <div className="flex shrink-0 items-center gap-3 text-xs">
          <button type="button" onClick={onEdit} className="font-medium text-ink-faint transition-colors hover:text-ink">
            Edit
          </button>
          {removeLabel ? (
            <button
              type="button"
              onClick={onDelete}
              disabled={deleting}
              aria-label={removeLabel}
              title={removeLabel}
              className="flex h-6 w-6 items-center justify-center rounded-full text-base leading-none text-ink-faint transition-colors hover:bg-rose/10 hover:text-rose disabled:opacity-50"
            >
              ×
            </button>
          ) : (
            <button
              type="button"
              onClick={onDelete}
              disabled={deleting}
              className="font-medium text-ink-faint transition-colors hover:text-rose disabled:opacity-50"
            >
              Delete
            </button>
          )}
        </div>
      </div>
    </Card>
  );
}
