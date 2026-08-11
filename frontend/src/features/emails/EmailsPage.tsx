import { useMemo, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { DashboardShell } from '@/features/dashboard/components/DashboardShell';
import { FilterChipGroup } from '@/features/dashboard/components/FilterChipGroup';
import { Pagination } from '@/features/dashboard/components/Pagination';
import { PageHeader } from '@/features/dashboard/components/PageHeader';
import { SearchInput } from '@/features/dashboard/components/SearchInput';
import { CopyIcon, MailIcon, PlusCircleIcon } from '@/features/dashboard/icons';
import { STATUS_FILTERS, formatDate, statusStyle, type StatusFilter } from '@/features/dashboard/utils';
import { getEmail, listApplications, type ApplicationSummary } from '@/services/applicationApi';

type SortOrder = 'NEWEST' | 'OLDEST';

const PAGE_SIZE = 10;
const FETCH_SIZE = 200;

function EmailRow({ item }: { item: ApplicationSummary }) {
  const copy = useMutation({
    mutationFn: async () => {
      const email = await getEmail(item.id);
      await navigator.clipboard.writeText(`${email.subject}\n\n${email.body}`);
    },
  });

  return (
    <li>
      <Card className="!py-4 transition-colors hover:border-border-strong">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="min-w-0">
            <p className="text-sm font-medium text-ink">{item.jobTitle ?? 'Untitled role'}</p>
            <p className="mt-0.5 text-xs text-ink-faint">{item.company ?? 'Unknown company'}</p>
            {copy.isError && <p className="mt-1.5 text-xs text-rose">Couldn't copy — try again.</p>}
          </div>
          <div className="flex shrink-0 flex-col items-end gap-2">
            <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${statusStyle(item.status)}`}>
              {item.status.charAt(0) + item.status.slice(1).toLowerCase()}
            </span>
            <span className="text-[11px] text-ink-faint">{formatDate(item.createdAt)}</span>
            <div className="flex items-center gap-2">
              <Button
                variant="secondary"
                className="!px-3 !py-2 !text-xs"
                loading={copy.isPending}
                onClick={() => copy.mutate()}
              >
                <CopyIcon className="h-3.5 w-3.5" />
                {copy.isSuccess ? 'Copied' : 'Copy'}
              </Button>
              <Link to={`/applications/${item.id}`}>
                <Button variant="secondary" className="!px-4 !py-2 !text-xs">
                  View
                </Button>
              </Link>
            </div>
          </div>
        </div>
      </Card>
    </li>
  );
}

/** There's no dedicated "list emails" endpoint — an email is one output field (`emailId`) on
 *  an `Application`, so this filters the same real `listApplications` data down to rows that
 *  actually have one, same approach as the Cover Letters page. */
export function EmailsPage() {
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
  const [sortOrder, setSortOrder] = useState<SortOrder>('NEWEST');
  const [page, setPage] = useState(0);

  const applicationsQuery = useQuery({
    queryKey: ['applications', 'all', statusFilter],
    queryFn: () => listApplications(statusFilter === 'ALL' ? undefined : statusFilter, 0, FETCH_SIZE),
  });

  const filtered = useMemo(() => {
    const all = applicationsQuery.data?.content ?? [];
    const q = search.trim().toLowerCase();
    const matches = all.filter((a) => {
      if (!a.emailId) return false;
      if (!q) return true;
      return (a.jobTitle ?? '').toLowerCase().includes(q) || (a.company ?? '').toLowerCase().includes(q);
    });
    return sortOrder === 'NEWEST' ? matches : [...matches].reverse();
  }, [applicationsQuery.data, search, sortOrder]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const currentPage = Math.min(page, totalPages - 1);
  const pageItems = filtered.slice(currentPage * PAGE_SIZE, currentPage * PAGE_SIZE + PAGE_SIZE);

  const resetToFirstPage = () => setPage(0);

  return (
    <DashboardShell>
      {({ user, onLogout }) => (
        <>
          <PageHeader
            title="Emails"
            description="Every outreach email you've generated, grounded in your real profile."
            user={user}
            onLogout={onLogout}
            action={
              <Link to="/generate/job?type=EMAIL_ONLY">
                <Button className="!px-4 !py-2.5 !text-sm">
                  <PlusCircleIcon className="h-4 w-4" />
                  Create new
                </Button>
              </Link>
            }
          />

          <Card className="mt-6 !p-5 sm:!p-6">
            <div className="flex flex-wrap items-center justify-between gap-4">
              <SearchInput
                value={search}
                onChange={(v) => {
                  setSearch(v);
                  resetToFirstPage();
                }}
                placeholder="Search by job title or company…"
              />
              <FilterChipGroup
                options={[
                  { id: 'NEWEST' as SortOrder, label: 'Newest first' },
                  { id: 'OLDEST' as SortOrder, label: 'Oldest first' },
                ]}
                value={sortOrder}
                onChange={(v) => {
                  setSortOrder(v);
                  resetToFirstPage();
                }}
              />
            </div>
            <div className="mt-4">
              <FilterChipGroup
                options={STATUS_FILTERS}
                value={statusFilter}
                onChange={(v) => {
                  setStatusFilter(v);
                  resetToFirstPage();
                }}
              />
            </div>

            <div className="mt-5">
              {applicationsQuery.isLoading && <p className="text-sm text-ink-faint">Loading…</p>}
              {applicationsQuery.isError && <ErrorBanner error={applicationsQuery.error} />}
              {applicationsQuery.data && filtered.length === 0 && (
                <EmptyState
                  icon={<MailIcon className="h-5 w-5" />}
                  title={statusFilter === 'ALL' && !search ? "You haven't generated an email yet." : 'No emails match these filters.'}
                  hint="Compose an outreach email tailored to a job description in seconds."
                  action={
                    <Link to="/generate/job?type=EMAIL_ONLY">
                      <Button className="!px-5 !py-2.5 !text-sm">Compose your first email</Button>
                    </Link>
                  }
                />
              )}
              {pageItems.length > 0 && (
                <ul className="space-y-3">
                  {pageItems.map((item) => (
                    <EmailRow key={item.id} item={item} />
                  ))}
                </ul>
              )}
            </div>

            {filtered.length > 0 && <Pagination page={currentPage} totalPages={totalPages} onPageChange={setPage} />}
          </Card>
        </>
      )}
    </DashboardShell>
  );
}
