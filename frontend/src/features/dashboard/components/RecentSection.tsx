import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Card } from '@/components/ui/Card';

/** A Dashboard summary section — a title, an optional one-line description, a real "View all"
 *  link to the section's dedicated page, and whatever short (≤5 item) list the caller renders
 *  as `children`. Shared by every "Recent …" card on the Dashboard so the pattern (and the
 *  promise that it never shows more than a handful of rows) stays identical across all of
 *  them. */
export function RecentSection({
  id,
  title,
  description,
  viewAllHref,
  children,
}: {
  id?: string;
  title: string;
  description?: string;
  viewAllHref: string;
  children: ReactNode;
}) {
  return (
    <Card {...(id !== undefined ? { id } : {})} className="scroll-mt-8 !p-5 sm:!p-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-base font-semibold text-ink">{title}</h2>
          {description && <p className="mt-0.5 text-xs text-ink-faint">{description}</p>}
        </div>
        <Link
          to={viewAllHref}
          className="shrink-0 text-xs font-medium text-ember-soft transition-colors hover:text-ember-soft/80"
        >
          View all →
        </Link>
      </div>
      <div className="mt-4">{children}</div>
    </Card>
  );
}
