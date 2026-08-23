import { useNavigate, useParams } from 'react-router-dom';
import { DocumentIcon, MailIcon, SparkleIcon } from '@/features/landing/components/icons';
import { DashboardSidebar } from '@/features/dashboard/components/DashboardSidebar';
import { useSession } from '@/services/session';
import { GenerationProgress } from './components/GenerationProgress';
import { ReviewHeader } from './components/ReviewHeader';

/**
 * `id` is the value carried in `/generate/processing/:jdId?type=` — for the resume path this is
 * still `JD_OPTIMIZATION` (a jd-service concept, not an `Application` `GenerationType`; see the
 * comment this file always carried), because that's what ProcessingPage already branches on and
 * what actually computes/reuses the optimization + resume-render pipeline (ADR-033/ADR-036) —
 * nothing new to build, this step only decides which existing pipeline the user wants.
 *
 * Cover Letter and "All" are real, named options in the product's intended shape (`GenerationType`
 * in `services/applicationApi.ts` already reserves `COVER_LETTER_ONLY` and `ALL`), but neither has
 * generation logic behind it yet — `GenerationType`'s own doc comment says as much. Shown as
 * locked "Coming Soon" cards (the same honest pattern this page already used before this step
 * moved), never as a selectable option that would silently fail at generation time.
 */
const cards = [
  {
    id: 'JD_OPTIMIZATION',
    title: 'JD Optimized Resume',
    description:
      'A resume PDF assembled straight from your verified evidence and this role’s optimization — no AI rephrasing.',
    icon: DocumentIcon,
    available: true,
  },
  {
    id: 'EMAIL_ONLY',
    title: 'Email Content',
    description: 'Application email subject and body.',
    icon: MailIcon,
    available: true,
  },
  {
    id: 'COVER_LETTER_ONLY',
    title: 'Cover Letter',
    description: 'A grounded cover letter drafted from your verified evidence.',
    icon: SparkleIcon,
    available: false,
  },
  {
    id: 'ALL',
    title: 'All of them',
    description: 'Resume, cover letter and email in one pass.',
    icon: SparkleIcon,
    available: false,
  },
] as const;

export function OutputTypePage() {
  const { jdId = '' } = useParams<{ jdId: string }>();
  const { data: user } = useSession();
  const navigate = useNavigate();

  if (!user) return null;
  const displayName = user.displayName?.trim() || user.email;

  return (
    <div className="flex min-h-screen bg-void">
      <DashboardSidebar userName={displayName} userEmail={user.email} />

      <div className="flex min-w-0 flex-1 flex-col">
        <ReviewHeader
          title="Generate application"
          backLabel="Back to skill gaps"
          backTo={`/generate/skill-gap/${jdId}`}
          showSaveDraft={false}
        />

        <main className="min-w-0 flex-1 px-5 py-7 pl-16 sm:px-7 lg:px-10 lg:py-9 lg:pl-10">
          <div className="mx-auto max-w-[1680px]">
            <GenerationProgress activeStep={2} />

            <h1 className="mt-6 text-2xl font-semibold tracking-tight text-ink sm:text-[28px]">
              What do you want to create?
            </h1>
            <p className="mt-1.5 max-w-2xl text-sm text-ink-muted">
              CareerForge already analyzed your fit against this role and closed the skill gaps you
              chose to close — pick what to build from it.
            </p>

            <div className="mt-8 grid gap-4 sm:grid-cols-2">
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
                    onClick={() => navigate(`/generate/processing/${jdId}?type=${card.id}`)}
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
          </div>
        </main>
      </div>
    </div>
  );
}
