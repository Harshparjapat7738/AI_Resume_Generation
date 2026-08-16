import type { ComponentType } from 'react';
import { CheckIcon } from './icons';

interface Badge {
  icon: ComponentType<{ className?: string }>;
  label: string;
  position: 'top-left' | 'top-right' | 'bottom-left' | 'bottom-right';
}

const POSITION_CLASS: Record<Badge['position'], string> = {
  'top-left': '-left-4 -top-4 sm:-left-8 sm:-top-6',
  'top-right': '-right-4 -top-6 sm:-right-8 sm:-top-8',
  'bottom-left': '-bottom-4 -left-6 sm:-bottom-6 sm:-left-10',
  'bottom-right': '-bottom-6 -right-4 sm:-bottom-8 sm:-right-8',
};

/**
 * A reusable "workspace" scene — an abstract document/profile card with a couple of
 * floating icon badges around it — standing in for a literal person/robot illustration
 * (redesign brief §5 and §9 both ask for one). Built from the same stroke-icon language
 * as the rest of the page rather than a differently-styled illustration or a stock photo,
 * so it reuses `badges` per caller instead of two near-duplicate components.
 */
export function WorkspaceIllustration({ badges }: { badges: Badge[] }) {
  return (
    <div className="relative mx-auto max-w-sm">
      <div
        aria-hidden="true"
        className="absolute inset-0 -z-10 rounded-full bg-brand/15 blur-3xl"
      />

      <div className="animate-float rounded-3xl border border-border bg-surface p-6 shadow-2xl shadow-brand/10">
        <div className="flex items-center gap-3">
          <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-linear-to-br from-brand to-brand-2 text-void">
            <CheckIcon className="h-5 w-5" />
          </span>
          <div className="min-w-0 flex-1 space-y-2">
            <div className="h-2.5 w-3/4 rounded-full bg-surface-2" />
            <div className="h-2.5 w-1/2 rounded-full bg-surface-2" />
          </div>
        </div>

        <div className="mt-6 space-y-2.5">
          <div className="h-2 w-full rounded-full bg-surface-2" />
          <div className="h-2 w-11/12 rounded-full bg-surface-2" />
          <div className="h-2 w-4/5 rounded-full bg-surface-2" />
        </div>

        <div className="mt-6 grid grid-cols-3 gap-2.5">
          {[0, 1, 2].map((i) => (
            <div key={i} className="h-14 rounded-xl bg-cream" />
          ))}
        </div>
      </div>

      {badges.map((badge) => (
        <div
          key={badge.label}
          className={`animate-float-delayed absolute hidden items-center gap-2 rounded-2xl border border-border bg-surface px-3.5 py-2.5 shadow-xl shadow-black/5 sm:flex ${POSITION_CLASS[badge.position]}`}
        >
          <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-brand/10 text-brand">
            <badge.icon className="h-4 w-4" />
          </span>
          <span className="whitespace-nowrap text-xs font-medium text-ink">{badge.label}</span>
        </div>
      ))}
    </div>
  );
}
