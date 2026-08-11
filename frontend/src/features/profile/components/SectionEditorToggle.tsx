import type { ReactNode } from 'react';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';

/**
 * Collapses a record's add/edit form behind a trigger button instead of leaving it
 * permanently open on the page — used by every repeatable section's "dashboard" layout.
 * Purely a show/hide shell: the form passed as `children` keeps its own react-hook-form
 * state exactly as before, so nothing about validation or submission changes.
 */
export function SectionEditorToggle({
  open,
  addLabel,
  onOpen,
  children,
}: {
  open: boolean;
  addLabel: string;
  onOpen: () => void;
  children: ReactNode;
}) {
  if (!open) {
    return (
      <Button type="button" variant="secondary" onClick={onOpen} className="w-full sm:w-auto animate-card-in">
        + {addLabel}
      </Button>
    );
  }
  return <Card className="animate-card-in">{children}</Card>;
}
