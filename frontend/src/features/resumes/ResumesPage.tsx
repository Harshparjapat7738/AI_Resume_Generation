import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
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
import { useDownloadResumePdf } from '@/features/dashboard/hooks';
import { DocumentIcon, DownloadIcon, PlusCircleIcon } from '@/features/dashboard/icons';
import { formatDate } from '@/features/dashboard/utils';
import { listApplications } from '@/services/applicationApi';
import { listResumes } from '@/services/resumeApi';
import {
  applicationsToResumeItems,
  mergeResumeItemsByDate,
  standaloneToResumeItems,
  type ResumeListItem,
  type ResumeSource,
} from './resumeListUtils';

type SortOrder = 'NEWEST' | 'OLDEST';
type SourceFilter = 'ALL' | ResumeSource;

const PAGE_SIZE = 10;
const FETCH_SIZE = 200;

const SOURCE_FILTERS: { id: SourceFilter; label: string }[] = [
  { id: 'ALL', label: 'All' },
  { id: 'APPLICATION', label: 'From application' },
  { id: 'STANDALONE', label: 'Standalone' },
];

function ResumeRow({ item }: { item: ResumeListItem }) {
  const download = useDownloadResumePdf();
  return (
    <li>
      <Card className="!py-4 transition-colors hover:border-border-strong">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="min-w-0">
            <p className="text-sm font-medium text-ink">{item.jobTitle ?? 'Untitled role'}</p>
            <p className="mt-0.5 text-xs text-ink-faint">{item.company ?? 'Unknown company'}</p>
            <div className="mt-2 flex flex-wrap items-center gap-2">
              <span className="text-[11px] text-ink-faint">{formatDate(item.createdAt)}</span>
              <span className="rounded-full border border-border-strong px-2 py-0.5 text-[11px] text-ink-muted">
                {item.source === 'APPLICATION' ? 'From application' : 'Standalone'}
              </span>
            </div>
            {download.isError && <p className="mt-1.5 text-xs text-rose">Couldn't prepare the PDF. Try again.</p>}
          </div>
          <div className="flex shrink-0 items-center gap-2">
            <Button
              variant="secondary"
              className="!px-3 !py-2 !text-xs"
              loading={download.isPending}
              onClick={() =>
                download.mutate({ resumeVersionId: item.resumeVersionId, jobTitle: item.jobTitle, company: item.company })
              }
            >
              <DownloadIcon className="h-3.5 w-3.5" />
              Download
            </Button>
            <Link to={item.viewHref}>
              <Button variant="secondary" className="!px-4 !py-2 !text-xs">
                View
              </Button>
            </Link>
          </div>
        </div>
      </Card>
    </li>
  );
}

/** No dedicated "list all resumes" endpoint exists — a resume is either tracked on an
 *  `Application` (resumeVersionId) or, for resumes generated before Applications existed,
 *  standalone in resume-service alone (see resumeListUtils.ts). This page fetches both real
 *  sources and merges them into one dataset rather than fabricating a unified backend that
 *  doesn't exist. */
export function ResumesPage() {
  const [search, setSearch] = useState('');
  const [sourceFilter, setSourceFilter] = useState<SourceFilter>('ALL');
  const [sortOrder, setSortOrder] = useState<SortOrder>('NEWEST');
  const [page, setPage] = useState(0);

  const applicationsQuery = useQuery({
    queryKey: ['applications', 'summary'],
    queryFn: () => listApplications(undefined, 0, FETCH_SIZE),
  });
  const resumesQuery = useQuery({
    queryKey: ['resumes', 'all'],
    queryFn: () => listResumes(0, FETCH_SIZE),
  });

  const isLoading = applicationsQuery.isLoading || resumesQuery.isLoading;
  const isError = applicationsQuery.isError || resumesQuery.isError;

  const merged = useMemo(() => {
    const applications = applicationsQuery.data?.content ?? [];
    const standalone = resumesQuery.data?.content ?? [];
    return mergeResumeItemsByDate(applicationsToResumeItems(applications), standaloneToResumeItems(standalone));
  }, [applicationsQuery.data, resumesQuery.data]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    const matches = merged.filter((item) => {
      if (sourceFilter !== 'ALL' && item.source !== sourceFilter) return false;
      if (!q) return true;
      return (item.jobTitle ?? '').toLowerCase().includes(q) || (item.company ?? '').toLowerCase().includes(q);
    });
    return sortOrder === 'NEWEST' ? matches : [...matches].reverse();
  }, [merged, search, sourceFilter, sortOrder]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const currentPage = Math.min(page, totalPages - 1);
  const pageItems = filtered.slice(currentPage * PAGE_SIZE, currentPage * PAGE_SIZE + PAGE_SIZE);

  const resetToFirstPage = () => setPage(0);

  return (
    <DashboardShell>
      {({ user, onLogout }) => (
        <>
          <PageHeader
            title="Resumes"
            description="Every resume you've generated, from a full application or on its own."
            user={user}
            onLogout={onLogout}
            action={
              <Link to="/generate/job?type=RESUME_ONLY">
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
            <div className="mt-4">
              <FilterChipGroup
                options={SOURCE_FILTERS}
                value={sourceFilter}
                onChange={(v) => {
                  setSourceFilter(v);
                  resetToFirstPage();
                }}
              />
            </div>

            <div className="mt-5">
              {isLoading && <p className="text-sm text-ink-faint">Loading…</p>}
              {isError && <ErrorBanner error={applicationsQuery.error ?? resumesQuery.error} />}
              {!isLoading && filtered.length === 0 && (
                <EmptyState
                  icon={<DocumentIcon className="h-5 w-5" />}
                  title={sourceFilter === 'ALL' && !search ? "You haven't generated a resume yet." : 'No resumes match these filters.'}
                  hint="Generate a resume tailored to a job description in seconds."
                  action={
                    <Link to="/generate/job?type=RESUME_ONLY">
                      <Button className="!px-5 !py-2.5 !text-sm">Generate your first resume</Button>
                    </Link>
                  }
                />
              )}
              {pageItems.length > 0 && (
                <ul className="space-y-3">
                  {pageItems.map((item) => (
                    <ResumeRow key={item.key} item={item} />
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
