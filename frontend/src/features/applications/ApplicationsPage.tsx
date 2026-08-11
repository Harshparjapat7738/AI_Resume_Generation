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
import { DocumentIcon, PlusCircleIcon } from '@/features/dashboard/icons';
import { STATUS_FILTERS, TYPE_FILTERS, type StatusFilter, type TypeFilter } from '@/features/dashboard/utils';
import { listApplications } from '@/services/applicationApi';

type SortOrder = 'NEWEST' | 'OLDEST';

const PAGE_SIZE = 10;

// The full application history — status filtering is a real server-side query param
// (applicationApi.listApplications), but type filtering, free-text search and sort are not
// (see applicationApi.ts: `listApplications(status?, page, size)` — no `q`, `type` or `sort`
// params). Rather than filter within one already-paginated server page (which would hide
// matches sitting on other pages), this fetches a generously-sized, status-filtered batch
// once and does type/search/sort/pagination over that — see `FETCH_SIZE`.
const FETCH_SIZE = 200;

export function ApplicationsPage() {
  const [search, setSearch] = useState('');
  const [typeFilter, setTypeFilter] = useState<TypeFilter>('ALL');
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
      if (typeFilter !== 'ALL' && a.generationType !== typeFilter) return false;
      if (!q) return true;
      return (a.jobTitle ?? '').toLowerCase().includes(q) || (a.company ?? '').toLowerCase().includes(q);
    });
    // The server already returns newest-first; reverse for "oldest first" instead of
    // re-deriving a date comparator for the common case.
    return sortOrder === 'NEWEST' ? matches : [...matches].reverse();
  }, [applicationsQuery.data, search, typeFilter, sortOrder]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const currentPage = Math.min(page, totalPages - 1);
  const pageItems = filtered.slice(currentPage * PAGE_SIZE, currentPage * PAGE_SIZE + PAGE_SIZE);

  const resetToFirstPage = () => setPage(0);

  return (
    <DashboardShell>
      {({ user, onLogout }) => (
        <>
          <PageHeader
            title="Applications"
            description="Every application you've generated — search, filter and open any of them."
            user={user}
            onLogout={onLogout}
            action={
              <Link to="/generate">
                <Button className="!px-4 !py-2.5 !text-sm">
                  <PlusCircleIcon className="h-4 w-4" />
                  Generate new
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
            <div className="mt-4 flex flex-wrap gap-4">
              <FilterChipGroup
                options={TYPE_FILTERS}
                value={typeFilter}
                onChange={(v) => {
                  setTypeFilter(v);
                  resetToFirstPage();
                }}
              />
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
                  icon={<DocumentIcon className="h-5 w-5" />}
                  title={
                    typeFilter === 'ALL' && statusFilter === 'ALL' && !search
                      ? "You haven't generated an application yet."
                      : 'No applications match these filters.'
                  }
                  hint="Create your first application and let AI do the heavy lifting for you."
                  action={
                    <Link to="/generate">
                      <Button className="!px-5 !py-2.5 !text-sm">Generate your first application</Button>
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

            {filtered.length > 0 && (
              <Pagination page={currentPage} totalPages={totalPages} onPageChange={setPage} />
            )}
          </Card>
        </>
      )}
    </DashboardShell>
  );
}
