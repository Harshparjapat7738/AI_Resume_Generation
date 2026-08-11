import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';

const RADIUS = 32;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

/** Compact circular readout of the same `computeProfileCompletion` percentage the Profile
 *  page's own hero shows — literal hex values (not `var(--color-...)`) below because SVG
 *  gradient stops don't reliably resolve CSS custom properties/Tailwind utilities the way
 *  `stroke`/`fill` do; these must stay in sync with `--color-ember-soft`/`--color-rose` in
 *  index.css if that palette ever changes. */
export function ProfileCompletionWidget({ percentage }: { percentage: number }) {
  const offset = CIRCUMFERENCE * (1 - percentage / 100);
  const isComplete = percentage === 100;

  return (
    <Card className="!p-5">
      <p className="text-xs font-semibold uppercase tracking-wide text-ink-faint">Profile completion</p>
      <div className="mt-4 flex items-center gap-4">
        <div className="relative h-[72px] w-[72px] shrink-0">
          <svg viewBox="0 0 72 72" className="h-[72px] w-[72px] -rotate-90">
            <circle cx="36" cy="36" r={RADIUS} fill="none" strokeWidth="6" className="stroke-surface-2" />
            <circle
              cx="36"
              cy="36"
              r={RADIUS}
              fill="none"
              strokeWidth="6"
              strokeLinecap="round"
              stroke="url(#dashboard-completion-gradient)"
              strokeDasharray={CIRCUMFERENCE}
              strokeDashoffset={offset}
              className="transition-all duration-700 ease-out"
            />
            <defs>
              <linearGradient id="dashboard-completion-gradient" x1="0" y1="0" x2="1" y2="1">
                <stop offset="0%" stopColor="#ffb454" />
                <stop offset="100%" stopColor="#ff3d68" />
              </linearGradient>
            </defs>
          </svg>
          <span className="absolute inset-0 flex items-center justify-center text-sm font-semibold text-ink">
            {percentage}%
          </span>
        </div>
        <div className="min-w-0">
          <p className="text-sm font-medium text-ink">{isComplete ? 'Great job!' : 'Almost there'}</p>
          <p className="mt-0.5 text-xs text-ink-muted">
            {isComplete
              ? 'Your profile is complete.'
              : 'Finish your profile for stronger, more grounded results.'}
          </p>
        </div>
      </div>
      <Link to="/profile" className="mt-4 block">
        <Button type="button" variant="secondary" className="w-full !py-2 !text-sm">
          View profile
        </Button>
      </Link>
    </Card>
  );
}
