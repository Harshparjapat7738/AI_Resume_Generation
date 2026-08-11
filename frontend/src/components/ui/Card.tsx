import type { ReactNode } from 'react';

export function Card({
  children,
  className,
  id,
}: {
  children: ReactNode;
  className?: string;
  /** For a scroll-target section card (e.g. dashboard widgets deep-linked from the sidebar) —
   *  optional, everything else about Card is unchanged for callers that don't pass it. */
  id?: string;
}) {
  return (
    <div id={id} className={`rounded-2xl border border-border bg-surface p-6 sm:p-8 ${className ?? ''}`}>
      {children}
    </div>
  );
}
