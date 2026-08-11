/**
 * `/applications/:applicationId` — the canonical, generation-type-agnostic application detail
 * page the dashboard links to. Reuses `AllResultPage` as-is rather than duplicating its
 * Resume/Cover Letter/Email/ATS/JD-Fit tabs, downloads and per-output retry: that component
 * already renders correctly for any mix of outputs (an `EMAIL_ONLY` application simply shows
 * its Email tab populated and the others as "not generated yet"), which is exactly what a
 * generic application detail page needs. `/results/all/:applicationId` (the route the
 * "Generate All" processing step redirects to) keeps working unchanged — both routes render
 * the same component.
 */
export { AllResultPage as ApplicationDetailPage } from './AllResultPage';
