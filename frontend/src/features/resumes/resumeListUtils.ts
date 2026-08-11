/**
 * A "resume" the user can see is really two different underlying records — see
 * DashboardPage's original comment on `legacyResumesQuery`: most resumes are generated as part
 * of an `Application` (resume-service resume + application-service tracking row), but any
 * resume generated before Applications existed (or by a flow that still doesn't create one)
 * lives only in resume-service with no Application at all. Both are real, both are
 * downloadable, so both belong in one merged, date-sorted list — this is the shared shape
 * Dashboard's "Recent resumes" and the full /resumes page both build from, so they can never
 * disagree about what counts as a resume.
 */
import type { ApplicationSummary } from '@/services/applicationApi';
import type { ResumeSummary } from '@/services/resumeApi';

export type ResumeSource = 'APPLICATION' | 'STANDALONE';

export interface ResumeListItem {
  /** Stable React key — the resume version id either way. */
  key: string;
  resumeVersionId: string;
  applicationId: string | null;
  jobTitle: string | null;
  company: string | null;
  createdAt: string;
  source: ResumeSource;
  /** Where "View" should go — the application detail page (with its full Resume/Cover
   *  Letter/Email tabs) for application-linked resumes, or the standalone result page. */
  viewHref: string;
}

export function applicationsToResumeItems(applications: ApplicationSummary[]): ResumeListItem[] {
  return applications
    .filter((a) => Boolean(a.resumeVersionId))
    .map((a) => ({
      key: a.resumeVersionId as string,
      resumeVersionId: a.resumeVersionId as string,
      applicationId: a.id,
      jobTitle: a.jobTitle,
      company: a.company,
      createdAt: a.createdAt,
      source: 'APPLICATION' as const,
      viewHref: `/applications/${a.id}`,
    }));
}

export function standaloneToResumeItems(resumes: ResumeSummary[]): ResumeListItem[] {
  return resumes.map((r) => ({
    key: r.id,
    resumeVersionId: r.id,
    applicationId: null,
    jobTitle: r.jobTitle,
    company: r.company,
    createdAt: r.createdAt,
    source: 'STANDALONE' as const,
    viewHref: `/results/${r.id}`,
  }));
}

export function mergeResumeItemsByDate(...lists: ResumeListItem[][]): ResumeListItem[] {
  return lists.flat().sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
}
