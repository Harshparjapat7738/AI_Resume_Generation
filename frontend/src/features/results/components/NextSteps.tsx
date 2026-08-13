import { Link } from 'react-router-dom';
import { ChevronRightIcon, DocumentIcon, GridIcon, LayersIcon, UserIcon } from '@/features/dashboard/icons';

const STEPS = [
  {
    label: 'Edit Profile',
    description: 'Update your skills & experience',
    to: '/profile',
    icon: UserIcon,
    iconClassName: 'bg-ember/10 text-ember-soft',
  },
  {
    label: 'Add Missing Skills',
    description: 'Boost keyword match',
    to: '/profile',
    icon: GridIcon,
    iconClassName: 'bg-mint/10 text-mint',
  },
  {
    label: 'Start Another Application',
    description: 'Generate for a new job',
    to: '/generate',
    icon: DocumentIcon,
    iconClassName: 'bg-rose/10 text-rose',
  },
  {
    label: 'View All Applications',
    description: 'See your application history',
    to: '/applications',
    icon: LayersIcon,
    iconClassName: 'bg-surface-2 text-ink-muted',
  },
] as const;

/** Every destination here is a real, already-routed page (routes/router.tsx) — the same
 *  pattern DashboardPage's own QuickActions cards use — never a placeholder link. */
export function NextSteps() {
  return (
    <div className="rounded-2xl border border-border bg-surface p-6 sm:p-8">
      <p className="text-xs font-semibold uppercase tracking-wide text-ink-faint">Next steps</p>
      <h2 className="mt-1.5 text-lg font-semibold text-ink">Improve your profile to increase ATS and job match scores</h2>
      <div className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {STEPS.map((step) => (
          <Link
            key={step.label}
            to={step.to}
            className="group flex flex-col justify-between gap-4 rounded-xl border border-border-strong p-5 transition-colors hover:border-ink-muted hover:bg-surface-2/50"
          >
            <span className={`flex h-10 w-10 items-center justify-center rounded-lg ${step.iconClassName}`}>
              <step.icon className="h-5 w-5" />
            </span>
            <div>
              <p className="text-sm font-medium text-ink">{step.label}</p>
              <p className="mt-0.5 text-xs text-ink-faint">{step.description}</p>
            </div>
            <ChevronRightIcon className="h-4 w-4 text-ink-faint transition-transform group-hover:translate-x-0.5 group-hover:text-ink-muted" />
          </Link>
        ))}
      </div>
    </div>
  );
}
