import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { getAssessment } from '@/services/assessmentApi';
import type { ApplicationSummary } from '@/services/applicationApi';
import { formatDate, generationTypeLabel, statusStyle, templateLabel } from '../utils';

/** JD fit is scored per job description now, not per resume version (ADR-033) — this fetches
 *  and renders it inline for a row, silently showing nothing if none has been run yet rather
 *  than an error (a missing assessment on a list row isn't something to alarm the user with).
 *  ATS structural scoring is back (ADR-040, scoped to pre-render content only) but isn't shown
 *  in this compact row — see the "Your scores" card on `OptimizationResultPage`. */
function AssessmentSummary({ jobDescriptionId }: { jobDescriptionId: string }) {
  const query = useQuery({
    queryKey: ['assessment', jobDescriptionId],
    queryFn: () => getAssessment(jobDescriptionId),
    enabled: Boolean(jobDescriptionId),
    retry: false,
  });
  if (!query.data) return null;
  return (
    <p className="mt-1 text-xs text-ink-muted">
      JD Match: {Math.round(query.data.compatibilityScore * 100)}%
    </p>
  );
}

/** One application row — used both by Dashboard's "Recent applications" (max 5) and the full
 *  Applications list page, so the two never render the same data with subtly different
 *  markup/labels. */
export function ApplicationRow({ item }: { item: ApplicationSummary }) {
  const template = templateLabel(item.templateId ?? null);
  return (
    <li>
      <Card className="!py-4 transition-colors hover:border-border-strong">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="min-w-0">
            <p className="text-sm font-medium text-ink">{item.jobTitle ?? 'Untitled role'}</p>
            <p className="mt-0.5 text-xs text-ink-faint">{item.company ?? 'Unknown company'}</p>
            <p className="mt-2 text-xs text-ink-muted">{generationTypeLabel(item.generationType)}</p>
            {template && <p className="mt-0.5 text-[11px] text-ink-faint">Template: {template}</p>}
            {item.jobDescriptionId && <AssessmentSummary jobDescriptionId={item.jobDescriptionId} />}
          </div>
          <div className="flex shrink-0 flex-col items-end gap-2">
            <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${statusStyle(item.status)}`}>
              {item.status.charAt(0) + item.status.slice(1).toLowerCase()}
            </span>
            <span className="text-[11px] text-ink-faint">{formatDate(item.createdAt)}</span>
            <Link to={`/applications/${item.id}`}>
              <Button variant="secondary" className="!px-4 !py-2 !text-xs">
                View Application
              </Button>
            </Link>
          </div>
        </div>
      </Card>
    </li>
  );
}
