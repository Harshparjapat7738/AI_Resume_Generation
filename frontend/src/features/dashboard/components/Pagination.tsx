import { ChevronLeftIcon, ChevronRightIcon } from '../icons';

/** Zero-indexed page control shared by every dedicated data page. Works the same way whether
 *  the page it's paginating came from a real server-side page (Applications/Resumes) or a
 *  client-side slice of a larger fetched batch (Cover Letters/Emails/derived views) — it only
 *  ever needs to know the current page and how many pages there are. */
export function Pagination({
  page,
  totalPages,
  onPageChange,
}: {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}) {
  if (totalPages <= 1) return null;
  return (
    <div className="mt-6 flex flex-wrap items-center justify-between gap-4 border-t border-border pt-4">
      <p className="text-xs text-ink-faint">
        Page {page + 1} of {totalPages}
      </p>
      <div className="flex items-center gap-2">
        <button
          type="button"
          disabled={page === 0}
          onClick={() => onPageChange(page - 1)}
          className="flex items-center gap-1 rounded-full border border-border-strong px-3 py-1.5 text-xs font-medium text-ink-muted transition-colors hover:text-ink disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:text-ink-muted"
        >
          <ChevronLeftIcon className="h-3.5 w-3.5" />
          Previous
        </button>
        <button
          type="button"
          disabled={page >= totalPages - 1}
          onClick={() => onPageChange(page + 1)}
          className="flex items-center gap-1 rounded-full border border-border-strong px-3 py-1.5 text-xs font-medium text-ink-muted transition-colors hover:text-ink disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:text-ink-muted"
        >
          Next
          <ChevronRightIcon className="h-3.5 w-3.5" />
        </button>
      </div>
    </div>
  );
}
