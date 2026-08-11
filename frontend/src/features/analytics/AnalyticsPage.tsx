import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Card } from '@/components/ui/Card';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { DashboardShell } from '@/features/dashboard/components/DashboardShell';
import { PageHeader } from '@/features/dashboard/components/PageHeader';
import { SummaryCard } from '@/features/dashboard/components/SummaryCard';
import { BarChartIcon, DocumentIcon, MailIcon, SendIcon } from '@/features/dashboard/icons';
import { generationTypeLabel, statusStyle } from '@/features/dashboard/utils';
import { listApplications, type ApplicationStatus, type GenerationType } from '@/services/applicationApi';
import { listResumes } from '@/services/resumeApi';

const STATUS_ORDER: ApplicationStatus[] = ['COMPLETED', 'PROCESSING', 'FAILED', 'DRAFT'];
const TYPE_ORDER: GenerationType[] = ['RESUME_ONLY', 'COVER_LETTER_ONLY', 'EMAIL_ONLY', 'ALL'];

const FETCH_SIZE = 200;

function monthKey(iso: string): string {
  const d = new Date(iso);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

function monthLabel(key: string): string {
  const [year, month] = key.split('-').map(Number);
  return new Date(year!, (month ?? 1) - 1, 1).toLocaleDateString('en-GB', { month: 'short', year: '2-digit' });
}

/** Every number on this page is derived from data the real `listApplications`/`listResumes`
 *  endpoints already return — there is no aggregate/stats endpoint anywhere in the API surface
 *  (see docs/API_CATALOG.md), so nothing here is a fabricated metric; it's all computed
 *  client-side from the same records the Applications/Resumes pages show in full. */
export function AnalyticsPage() {
  const applicationsQuery = useQuery({
    queryKey: ['applications', 'summary'],
    queryFn: () => listApplications(undefined, 0, FETCH_SIZE),
  });
  const resumesQuery = useQuery({ queryKey: ['resumes', 'all'], queryFn: () => listResumes(0, FETCH_SIZE) });

  const isLoading = applicationsQuery.isLoading || resumesQuery.isLoading;
  const isError = applicationsQuery.isError || resumesQuery.isError;
  const applications = applicationsQuery.data?.content ?? [];

  const stats = useMemo(() => {
    const total = applications.length;
    const byStatus = new Map<ApplicationStatus, number>();
    const byType = new Map<GenerationType, number>();
    const byMonth = new Map<string, number>();
    for (const app of applications) {
      byStatus.set(app.status, (byStatus.get(app.status) ?? 0) + 1);
      byType.set(app.generationType, (byType.get(app.generationType) ?? 0) + 1);
      const key = monthKey(app.createdAt);
      byMonth.set(key, (byMonth.get(key) ?? 0) + 1);
    }
    const months = [...byMonth.entries()].sort(([a], [b]) => (a < b ? -1 : 1)).slice(-6);
    const maxMonthCount = Math.max(1, ...months.map(([, count]) => count));
    const completed = byStatus.get('COMPLETED') ?? 0;
    const failed = byStatus.get('FAILED') ?? 0;
    const completionRate = total > 0 ? Math.round((completed / total) * 100) : 0;
    return { total, byStatus, byType, months, maxMonthCount, completed, failed, completionRate };
  }, [applications]);

  const resumeCount = (resumesQuery.data?.totalElements ?? 0) + applications.filter((a) => a.resumeVersionId).length;
  const coverLetterCount = applications.filter((a) => a.coverLetterVersionId).length;
  const emailCount = applications.filter((a) => a.emailId).length;

  return (
    <DashboardShell>
      {({ user, onLogout }) => (
        <>
          <PageHeader
            title="Analytics"
            description="How your applications, resumes, cover letters and emails are trending."
            user={user}
            onLogout={onLogout}
          />

          {isLoading && <p className="mt-6 text-sm text-ink-faint">Loading…</p>}
          {isError && <ErrorBanner error={applicationsQuery.error ?? resumesQuery.error} />}

          {!isLoading && !isError && stats.total === 0 && (
            <Card className="mt-6">
              <EmptyState
                icon={<BarChartIcon className="h-5 w-5" />}
                title="Nothing to analyse yet."
                hint="Generate your first application and your stats will appear here."
                action={
                  <Link to="/generate" className="text-sm font-medium text-ember-soft hover:text-ember-soft/80">
                    Generate your first application →
                  </Link>
                }
              />
            </Card>
          )}

          {!isLoading && !isError && stats.total > 0 && (
            <>
              <div className="mt-6 grid grid-cols-2 gap-4 lg:grid-cols-4">
                <SummaryCard
                  icon={<DocumentIcon className="h-5 w-5" />}
                  iconClassName="bg-ember/10 text-ember-soft"
                  label="Applications"
                  count={stats.total}
                  description="Total applications"
                />
                <SummaryCard
                  icon={<DocumentIcon className="h-5 w-5" />}
                  iconClassName="bg-mint/10 text-mint"
                  label="Resumes"
                  count={resumeCount}
                  description="Total resumes"
                />
                <SummaryCard
                  icon={<SendIcon className="h-5 w-5" />}
                  iconClassName="bg-rose/10 text-rose"
                  label="Cover letters"
                  count={coverLetterCount}
                  description="Total cover letters"
                />
                <SummaryCard
                  icon={<MailIcon className="h-5 w-5" />}
                  iconClassName="bg-surface-2 text-ink-muted"
                  label="Emails"
                  count={emailCount}
                  description="Total emails"
                />
              </div>

              <div className="mt-6 grid gap-6 lg:grid-cols-2">
                <Card className="!p-5 sm:!p-6">
                  <h2 className="text-base font-semibold text-ink">Activity, last 6 months</h2>
                  <p className="mt-0.5 text-xs text-ink-faint">Applications created per month.</p>
                  <div className="mt-6 flex h-40 items-end gap-3">
                    {stats.months.map(([key, count]) => (
                      <div key={key} className="flex flex-1 flex-col items-center gap-2">
                        <span className="text-xs font-medium text-ink">{count}</span>
                        <div
                          className="w-full rounded-t-md bg-linear-to-t from-ember-soft to-rose"
                          style={{ height: `${Math.max(6, (count / stats.maxMonthCount) * 100)}%` }}
                        />
                        <span className="text-[11px] text-ink-faint">{monthLabel(key)}</span>
                      </div>
                    ))}
                  </div>
                </Card>

                <Card className="!p-5 sm:!p-6">
                  <h2 className="text-base font-semibold text-ink">Completion rate</h2>
                  <p className="mt-0.5 text-xs text-ink-faint">
                    {stats.completed} of {stats.total} applications completed successfully.
                  </p>
                  <div className="mt-6 flex items-center gap-4">
                    <div className="relative h-24 w-24 shrink-0">
                      <svg viewBox="0 0 72 72" className="h-24 w-24 -rotate-90">
                        <circle cx="36" cy="36" r="32" fill="none" strokeWidth="7" className="stroke-surface-2" />
                        <circle
                          cx="36"
                          cy="36"
                          r="32"
                          fill="none"
                          strokeWidth="7"
                          strokeLinecap="round"
                          className="stroke-mint transition-all duration-700 ease-out"
                          strokeDasharray={2 * Math.PI * 32}
                          strokeDashoffset={2 * Math.PI * 32 * (1 - stats.completionRate / 100)}
                        />
                      </svg>
                      <span className="absolute inset-0 flex items-center justify-center text-lg font-semibold text-ink">
                        {stats.completionRate}%
                      </span>
                    </div>
                    <div className="min-w-0 space-y-1.5">
                      {STATUS_ORDER.map((status) => {
                        const count = stats.byStatus.get(status) ?? 0;
                        if (count === 0) return null;
                        return (
                          <div key={status} className="flex items-center gap-2 text-xs">
                            <span className={`rounded-full px-2 py-0.5 font-medium ${statusStyle(status)}`}>
                              {status.charAt(0) + status.slice(1).toLowerCase()}
                            </span>
                            <span className="text-ink-muted">{count}</span>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                </Card>
              </div>

              <Card className="mt-6 !p-5 sm:!p-6">
                <h2 className="text-base font-semibold text-ink">Applications by output type</h2>
                <p className="mt-0.5 text-xs text-ink-faint">What you generate most often.</p>
                <div className="mt-5 space-y-3">
                  {TYPE_ORDER.map((type) => {
                    const count = stats.byType.get(type) ?? 0;
                    const pct = stats.total > 0 ? Math.round((count / stats.total) * 100) : 0;
                    return (
                      <div key={type}>
                        <div className="flex items-center justify-between text-xs">
                          <span className="text-ink-muted">{generationTypeLabel(type)}</span>
                          <span className="text-ink-faint">
                            {count} · {pct}%
                          </span>
                        </div>
                        <div className="mt-1.5 h-2 overflow-hidden rounded-full bg-surface-2">
                          <div
                            className="h-full rounded-full bg-linear-to-r from-ember-soft to-rose"
                            style={{ width: `${pct}%` }}
                          />
                        </div>
                      </div>
                    );
                  })}
                </div>
              </Card>
            </>
          )}
        </>
      )}
    </DashboardShell>
  );
}
