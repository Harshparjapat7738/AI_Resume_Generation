import { useNavigate } from 'react-router-dom';
import { MailIcon, SparkleIcon } from '@/features/landing/components/icons';
import type { GenerationType } from '@/services/applicationApi';
import { GenerateLayout } from './GenerateLayout';

const cards = [
  {
    // Not a GenerationType: JD optimization is a jd-service concept, not an Application
    // generation type (ADR-033). Email below still is one, and is untouched.
    id: 'JD_OPTIMIZATION',
    title: 'JD Optimization',
    description:
      'Structured, JD-optimized data from your verified profile — keywords, matches and gaps you can take to any document tool.',
    icon: SparkleIcon,
    available: true,
  },
  {
    id: 'EMAIL_ONLY' satisfies GenerationType,
    title: 'Email Content',
    description: 'Application email subject and body.',
    icon: MailIcon,
    available: true,
  },
] as const;

export function OutputTypePage() {
  const navigate = useNavigate();

  return (
    <GenerateLayout
      activeStep={0}
      title="What do you want to create?"
      subtitle="CareerForge analyses your profile against a job description and gives you the data to build your documents — plus grounded application email content."
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
