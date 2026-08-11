import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { ApplicationRow } from '@/features/dashboard/components/ApplicationRow';
import { DashboardShell } from '@/features/dashboard/components/DashboardShell';
import { FilterChipGroup } from '@/features/dashboard/components/FilterChipGroup';
import { Pagination } from '@/features/dashboard/components/Pagination';
import { PageHeader } from '@/features/dashboard/components/PageHeader';
import { SearchInput } from '@/features/dashboard/components/SearchInput';
import { PlusCircleIcon, SendIcon } from '@/features/dashboard/icons';
import { STATUS_FILTERS, type StatusFilter } from '@/features/dashboard/utils';
import { listApplications } from '@/services/applicationApi';

type SortOrder = 'NEWEST' | 'OLDEST';

const PAGE_SIZE = 10;
const FETCH_SIZE = 200;

/** There's no dedicated "list cover letters" endpoint — a cover letter is one output field
 *  (`coverLetterVersionId`) on an `Application`, so this filters the same real
 *  `listApplications` data the Applications page uses down to rows that actually have one,
 *  rather than inventing a separate backend. */
export function CoverLettersPage() {
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
      if (!a.coverLetterVersionId) return false;
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
            title="Cover Letters"
            description="Every cover letter you've generated, grounded in your real profile."
            user={user}
            onLogout={onLogout}
            action={
              <Link to="/generate/job?type=COVER_LETTER_ONLY">
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
                  icon={<SendIcon className="h-5 w-5" />}
                  title={statusFilter === 'ALL' && !search ? "You haven't generated a cover letter yet." : 'No cover letters match these filters.'}
                  hint="Write a cover letter tailored to a job description in seconds."
                  action={
                    <Link to="/generate/job?type=COVER_LETTER_ONLY">
                      <Button className="!px-5 !py-2.5 !text-sm">Write your first cover letter</Button>
                    </Link>
                  }
                />
              )}
              {pageItems.length > 0 && (
                <ul className="space-y-3">
                  {pageItems.map((item) => (
                    <ApplicationRow key={item.id} item={item} />
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
