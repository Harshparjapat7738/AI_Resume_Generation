/** Read-only chip for a technology/tool name — used for Experience and Project tag lists.
 *  Distinct from Skills' own chip, which is directly editable/removable. */
export function Tag({ children }: { children: string }) {
  return (
    <span className="inline-flex items-center rounded-full border border-border bg-surface-2 px-2.5 py-1 text-xs text-ink-muted">
      {children}
    </span>
  );
}
