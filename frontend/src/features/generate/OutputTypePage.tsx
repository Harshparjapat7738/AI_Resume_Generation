import { useNavigate } from 'react-router-dom';
import {
  DocumentIcon,
  MailIcon,
  SendIcon,
  SparkleIcon,
} from '@/features/landing/components/icons';
import type { GenerationType } from '@/services/applicationApi';
import { GenerateLayout } from './GenerateLayout';

const cards = [
  {
    id: 'RESUME_ONLY' satisfies GenerationType,
    title: 'Resume',
    description: 'A job-specific resume, grounded in your real experience.',
    icon: DocumentIcon,
    available: true,
  },
  {
    id: 'COVER_LETTER_ONLY' satisfies GenerationType,
    title: 'Cover Letter',
    description: 'A personalized cover letter for this role.',
    icon: SendIcon,
    available: true,
  },
  {
    id: 'EMAIL_ONLY' satisfies GenerationType,
    title: 'Email Content',
    description: 'Application email subject and body.',
    icon: MailIcon,
    available: true,
  },
  {
    id: 'ALL' satisfies GenerationType,
    title: 'Generate All',
    description: 'Resume, cover letter and email together, in one application.',
    icon: SparkleIcon,
    available: true,
  },
] as const;

export function OutputTypePage() {
  const navigate = useNavigate();

  return (
    <GenerateLayout
      activeStep={0}
      title="What do you want to generate?"
      subtitle="Resume, cover letter and email generation are all available today — separately or together."
    >
      <div className="grid gap-4 sm:grid-cols-2">
        {cards.map((card) => {
          const Icon = card.icon;
          if (!card.available) {
            return (
              <div
                key={card.id}
                className="relative flex flex-col justify-between rounded-2xl border border-border bg-surface/60 p-6 opacity-60"
              >
                <div>
                  <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-surface-2 text-ink-faint">
                    <Icon className="h-5 w-5" />
                  </span>
                  <h3 className="mt-4 text-base font-medium text-ink">{card.title}</h3>
                  <p className="mt-1.5 text-sm text-ink-faint">{card.description}</p>
                </div>
                <span className="mt-4 inline-flex w-fit items-center gap-1.5 rounded-full border border-border-strong px-2.5 py-1 text-xs text-ink-faint">
                  🔒 Coming Soon
                </span>
              </div>
            );
          }
          return (
            <button
              key={card.id}
              type="button"
              onClick={() => navigate(`/generate/job?type=${card.id}`)}
              className="flex flex-col justify-between rounded-2xl border border-border-strong bg-surface p-6 text-left transition-colors hover:border-ember-soft"
            >
              <div>
                <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-surface-2 text-ember-soft ring-1 ring-border-strong">
                  <Icon className="h-5 w-5" />
                </span>
                <h3 className="mt-4 text-base font-medium text-ink">{card.title}</h3>
                <p className="mt-1.5 text-sm text-ink-muted">{card.description}</p>
              </div>
              <span className="mt-4 inline-flex w-fit items-center gap-1.5 rounded-full bg-mint/10 px-2.5 py-1 text-xs font-medium text-mint">
                Available now
              </span>
            </button>
          );
        })}
      </div>
    </GenerateLayout>
  );
}
