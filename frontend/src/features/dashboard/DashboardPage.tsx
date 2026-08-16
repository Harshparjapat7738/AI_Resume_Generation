import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { ApplicationRow } from './components/ApplicationRow';
import { DashboardShell } from './components/DashboardShell';
import { ProfileCompletionWidget } from './components/ProfileCompletionWidget';
import { ProTipCard, QuickActions } from './components/QuickActions';
import { RecentSection } from './components/RecentSection';
import { SummaryCard } from './components/SummaryCard';
import {
  BadgeCheckIcon,
  BellIcon,
  DocumentIcon,
  LockIcon,
  MailIcon,
  PlusCircleIcon,
  ShieldCheckIcon,
  SparkleIcon,
} from './icons';
import { UserMenu } from '@/components/layout/UserMenu';
import { listApplications } from '@/services/applicationApi';
import * as profileApi from '@/services/profileApi';
import { computeProfileCompletion } from '@/services/profileApi';

// Dashboard is a summary/overview only — every section below caps itself at 5 rows and links
// to a dedicated page (routes/router.tsx) that owns the complete, searchable/filterable/
// paginated dataset. See docs comment on RecentSection for why the cap lives structurally
// (`.slice(0, RECENT_LIMIT)`) rather than as a convention someone could forget.
const RECENT_LIMIT = 5;

const VALUE_STRIP = [
  { icon: SparkleIcon, title: 'AI-powered', description: 'Smart content generation' },
  { icon: ShieldCheckIcon, title: 'ATS-friendly', description: 'Pass ATS with confidence' },
  { icon: LockIcon, title: 'Secure & private', description: 'Your data is protected' },
  { icon: BadgeCheckIcon, title: 'Expert-approved', description: 'Trusted by professionals' },
] as const;

export function DashboardPage() {
  // Single unfiltered fetch (page 0, 200) drives both the summary-card counts and every
  // "Recent …" section below — the server already returns newest-first (see
  // ApplicationRepository#findByUserIdOrderByCreatedAtDesc), so the first 5 rows of this one
  // result *are* the 5 most recent applications; no second, differently-filtered request
  // needed just to get "recent" vs "total". There's no separate stats/aggregate API to call
  // instead.
  const summaryQuery = useQuery({
    queryKey: ['applications', 'summary'],
    queryFn: () => listApplications(undefined, 0, 200),
  });
  const summaryApplications = summaryQuery.data?.content ?? [];
  const recentApplications = summaryApplications.slice(0, RECENT_LIMIT);
  const emailApplications = summaryApplications.filter((a) => a.emailId);


  const profileQuery = useQuery({ queryKey: ['profile'], queryFn: profileApi.getProfile });
  const completion = profileQuery.data ? computeProfileCompletion(profileQuery.data) : null;

  return (
    <DashboardShell>
      {({ user, displayName, onLogout }) => {
        const firstName = displayName.split(/\s+/)[0];
        return (
          <>
            {/* Page header */}
            <div className="flex flex-wrap items-center justify-between gap-4">
              <div>
                <h1 className="text-2xl font-semibold tracking-tight text-ink">
                  Welcome back, {firstName} <span aria-hidden="true">👋</span>
                </h1>
                <p className="mt-1 text-sm text-ink-muted">Let's build something amazing today.</p>
              </div>
              <div className="flex items-center gap-3">
                <Link to="/generate">
                  <Button className="!px-4 !py-2.5 !text-sm">
                    <PlusCircleIcon className="h-4 w-4" />
                    Generate new
                  </Button>
                </Link>
                <button
                  type="button"
                  aria-label="Notifications"
                  className="flex h-10 w-10 items-center justify-center rounded-full border border-border text-ink-muted transition-colors hover:border-border-strong hover:text-ink"
                >
                  <BellIcon className="h-[18px] w-[18px]" />
                </button>
                <UserMenu user={user} onLogout={onLogout} />
              </div>
            </div>

            {/* Summary cards */}
            <div className="mt-6 grid grid-cols-2 gap-4 lg:grid-cols-2">
              <SummaryCard
                icon={<MailIcon className="h-5 w-5" />}
                iconClassName="bg-surface-2 text-ink-muted"
                label="Emails"
                count={emailApplications.length}
                description="Total emails"
              />
            </div>

            {/* Main grid: recent activity beside profile/quick actions */}
            <div className="mt-6 grid gap-6 lg:grid-cols-[1fr_320px]">
              <div className="min-w-0 space-y-6">
                <RecentSection id="applications" title="Recent Applications" viewAllHref="/applications">
                  {summaryQuery.isLoading && <p className="text-sm text-ink-faint">Loading…</p>}
                  {summaryQuery.isError && <ErrorBanner error={summaryQuery.error} />}
                  {summaryQuery.data && recentApplications.length === 0 && (
                    <EmptyState
                      icon={<DocumentIcon className="h-5 w-5" />}
                      title="You haven't generated an application yet."
                      hint="Create your first application and let AI do the heavy lifting for you."
                      action={
                        <Link to="/generate">
                          <Button className="!px-5 !py-2.5 !text-sm">Generate your first application</Button>
                        </Link>
                      }
                    />
                  )}
                  {recentApplications.length > 0 && (
                    <ul className="space-y-3">
                      {recentApplications.map((item) => (
                        <ApplicationRow key={item.id} item={item} />
                      ))}
                    </ul>
                  )}
                </RecentSection>



                <RecentSection title="Recent Emails" viewAllHref="/emails">
                  {summaryQuery.isLoading && <p className="text-sm text-ink-faint">Loading…</p>}
                  {!summaryQuery.isLoading && emailApplications.length === 0 && (
                    <EmptyState title="No emails yet." hint="Generated emails will appear here." />
                  )}
                  {emailApplications.length > 0 && (
                    <ul className="space-y-3">
                      {emailApplications.slice(0, RECENT_LIMIT).map((item) => (
                        <ApplicationRow key={item.id} item={item} />
                      ))}
                    </ul>
                  )}
                </RecentSection>

              </div>

              {/* Right-side widgets */}
              <div className="space-y-6">
                {completion && <ProfileCompletionWidget percentage={completion.percentage} />}
                <QuickActions />
                <ProTipCard />
              </div>
            </div>

            {/* Bottom value strip */}
            <div className="mt-8 grid grid-cols-2 gap-4 border-t border-border pt-6 sm:grid-cols-4">
              {VALUE_STRIP.map((item) => (
                <div key={item.title} className="flex items-center gap-2.5">
                  <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-surface-2 text-ember-soft">
                    <item.icon className="h-4 w-4" />
                  </span>
                  <div className="min-w-0">
                    <p className="text-xs font-medium text-ink">{item.title}</p>
                    <p className="truncate text-[11px] text-ink-faint">{item.description}</p>
                  </div>
                </div>
              ))}
            </div>
          </>
        );
      }}
    </DashboardShell>
  );
}
