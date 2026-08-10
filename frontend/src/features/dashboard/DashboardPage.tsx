import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { AppHeader } from '@/components/layout/AppHeader';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { listApplications } from '@/services/applicationApi';
import { listResumes } from '@/services/resumeApi';

export function DashboardPage() {
  const historyQuery = useQuery({ queryKey: ['resumes', 0], queryFn: () => listResumes(0, 20) });

  // Cover letters live on the `Application` aggregate (application-service), not
  // resume-service, so they're a second, independent list rather than merged into the one
  // above — see ARCHITECTURE_DECISIONS.md ADR-017/ADR-020.
  const coverLettersQuery = useQuery({
    queryKey: ['applications', 'COVER_LETTER_ONLY', 0],
    queryFn: () => listApplications('COMPLETED', 0, 20),
  });
  const coverLetters = coverLettersQuery.data?.content.filter((a) => a.generationType === 'COVER_LETTER_ONLY') ?? [];

  return (
    <div className="min-h-screen bg-void">
      <AppHeader />
      <main className="mx-auto max-w-3xl px-6 py-12">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <p className="text-sm font-medium uppercase tracking-wide text-ember-soft">Dashboard</p>
            <h1 className="mt-1 text-2xl font-semibold tracking-tight text-ink">Recent applications</h1>
          </div>
          <Link to="/generate">
            <Button>Generate new</Button>
          </Link>
        </div>

        <div className="mt-8">
          {historyQuery.isLoading && <p className="text-sm text-ink-faint">Loading…</p>}
          {historyQuery.isError && <ErrorBanner error={historyQuery.error} />}
          {historyQuery.data && historyQuery.data.content.length === 0 && (
            <div className="rounded-2xl border border-border bg-surface p-8 text-center">
              <p className="text-sm text-ink-muted">You haven't generated anything yet.</p>
              <Link to="/generate" className="mt-3 inline-block text-sm font-medium text-ink underline underline-offset-2">
                Generate your first resume
              </Link>
            </div>
          )}
          {historyQuery.data && historyQuery.data.content.length > 0 && (
            <ul className="divide-y divide-border rounded-2xl border border-border bg-surface">
              {historyQuery.data.content.map((item) => (
                <li key={item.id}>
                  <Link
                    to={`/results/${item.id}`}
                    className="flex items-center justify-between gap-4 px-6 py-4 transition-colors hover:bg-surface-2"
                  >
                    <div>
                      <p className="text-sm font-medium text-ink">{item.jobTitle ?? 'Untitled role'}</p>
                      <p className="mt-0.5 text-xs text-ink-faint">
                        {item.company ?? 'Unknown company'} · {new Date(item.createdAt).toLocaleDateString()}
                      </p>
                    </div>
                    <span className="rounded-full border border-border-strong px-2.5 py-1 text-xs text-ink-muted">
                      Resume
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="mt-10">
          <h2 className="text-sm font-medium uppercase tracking-wide text-ink-faint">Cover letters</h2>
          <div className="mt-4">
            {coverLettersQuery.isLoading && <p className="text-sm text-ink-faint">Loading…</p>}
            {coverLettersQuery.isError && <ErrorBanner error={coverLettersQuery.error} />}
            {coverLettersQuery.data && coverLetters.length === 0 && (
              <div className="rounded-2xl border border-border bg-surface p-8 text-center">
                <p className="text-sm text-ink-muted">You haven't generated a cover letter yet.</p>
                <Link to="/generate" className="mt-3 inline-block text-sm font-medium text-ink underline underline-offset-2">
                  Generate your first cover letter
                </Link>
              </div>
            )}
            {coverLetters.length > 0 && (
              <ul className="divide-y divide-border rounded-2xl border border-border bg-surface">
                {coverLetters.map((item) => (
                  <li key={item.id}>
                    <Link
                      to={`/results/cover-letter/${item.id}`}
                      className="flex items-center justify-between gap-4 px-6 py-4 transition-colors hover:bg-surface-2"
                    >
                      <div>
                        <p className="text-sm font-medium text-ink">{item.jobTitle ?? 'Untitled role'}</p>
                        <p className="mt-0.5 text-xs text-ink-faint">
                          {item.company ?? 'Unknown company'} · {new Date(item.createdAt).toLocaleDateString()}
                        </p>
                      </div>
                      <span className="rounded-full border border-border-strong px-2.5 py-1 text-xs text-ink-muted">
                        Cover Letter
                      </span>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      </main>
    </div>
  );
}
