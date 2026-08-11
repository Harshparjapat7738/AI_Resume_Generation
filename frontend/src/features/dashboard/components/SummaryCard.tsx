import type { ReactNode } from 'react';
import { Card } from '@/components/ui/Card';

export function SummaryCard({
  icon,
  iconClassName,
  label,
  count,
  description,
}: {
  icon: ReactNode;
  /** Background/text pair for the icon badge — each card gets its own accent so the row
   *  reads at a glance, while the gradient itself stays reserved for primary actions. */
  iconClassName: string;
  label: string;
  count: number;
  description: string;
}) {
  return (
    <Card className="!p-5 transition-colors hover:border-border-strong">
      <div className="flex items-start gap-3">
        <span className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-xl ${iconClassName}`}>
          {icon}
        </span>
        <div className="min-w-0">
          <p className="text-sm text-ink-muted">{label}</p>
          <p className="mt-1 text-2xl font-semibold tracking-tight text-ink">{count}</p>
          <p className="mt-0.5 text-xs text-ink-faint">{description}</p>
        </div>
      </div>
    </Card>
  );
}
