import { Link } from 'react-router-dom';
import { Card } from '@/components/ui/Card';
import { ChevronRightIcon, DocumentIcon, MailIcon, SendIcon, StarIcon } from '../icons';

// The first three destinations are the same real `/generate/job?type=...` route
// OutputTypePage's own cards navigate to (see features/generate/OutputTypePage.tsx) — just
// reached directly instead of via that selection screen, since JobDescriptionPage already
// reads `type` from the query string on its own. "Browse templates" goes to the real
// dedicated Templates page (routes/router.tsx).
const ACTIONS = [
  { label: 'Create new resume', to: '/generate/job?type=RESUME_ONLY', icon: DocumentIcon, iconClassName: 'bg-ember/10 text-ember-soft' },
  { label: 'Write cover letter', to: '/generate/job?type=COVER_LETTER_ONLY', icon: SendIcon, iconClassName: 'bg-rose/10 text-rose' },
  { label: 'Compose email', to: '/generate/job?type=EMAIL_ONLY', icon: MailIcon, iconClassName: 'bg-mint/10 text-mint' },
] as const;

export function QuickActions() {
  return (
    <Card className="!p-5">
      <p className="text-sm font-semibold text-ink">Quick actions</p>
      <div className="mt-3 space-y-1">
        {ACTIONS.map((action) => (
          <Link
            key={action.label}
            to={action.to}
            className="flex items-center justify-between rounded-xl px-2 py-2.5 text-sm text-ink-muted transition-colors hover:bg-surface-2 hover:text-ink"
          >
            <span className="flex items-center gap-3">
              <span className={`flex h-8 w-8 items-center justify-center rounded-lg ${action.iconClassName}`}>
                <action.icon className="h-4 w-4" />
              </span>
              {action.label}
            </span>
            <ChevronRightIcon className="h-4 w-4 shrink-0 text-ink-faint" />
          </Link>
        ))}
      </div>
    </Card>
  );
}

export function ProTipCard() {
  return (
    <Card className="relative !p-5 overflow-hidden">
      <div className="relative flex items-start gap-3">
        <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-ember/10 text-ember-soft">
          <StarIcon className="h-4 w-4" />
        </span>
        <div>
          <p className="text-sm font-semibold text-ink">Pro tip</p>
          <p className="mt-1 text-xs text-ink-muted">
            Tailor your resume and cover letter for each job to increase your chances of
            getting noticed.
          </p>
        </div>
      </div>
    </Card>
  );
}
