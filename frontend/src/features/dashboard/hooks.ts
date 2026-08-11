import { useMutation } from '@tanstack/react-query';
import { ApiError } from '@/services/apiClient';
import { logout as logoutRequest } from '@/services/authApi';
import {
  downloadDocument,
  getRenderedDocument,
  renderResumePdf,
  type RenderedDocument,
} from '@/services/documentApi';
import { useSessionActions } from '@/services/session';

/** Same logout sequence AppHeader/DashboardPage already use — the cookie is httpOnly and
 *  server-revoked whether or not the request itself succeeds, so the local session is always
 *  cleared, and a hard navigation (not react-router's navigate()) avoids a ProtectedRoute
 *  render race against the now-stale session query. Shared here so every dedicated data page
 *  doesn't redefine it. */
export function useLogout() {
  const { clearSession } = useSessionActions();
  return async () => {
    try {
      await logoutRequest();
    } catch {
      // Clear the local session regardless — see comment above.
    }
    clearSession();
    window.location.assign('/');
  };
}

function pdfFilename(jobTitle: string | null, company: string | null): string {
  const base = [jobTitle, company].filter(Boolean).join(' - ') || 'resume';
  return `${base.replace(/[^a-z0-9 _-]/gi, '').trim() || 'resume'}.pdf`;
}

/** The same render-if-needed → download sequence ResultPage.tsx uses (document-service only
 *  renders on demand; a resume version generated before it existed, or never downloaded, has
 *  nothing to fetch yet — that 404 is expected, not an error). Shared so the Resumes list page
 *  can offer a real one-click Download without duplicating that pipeline. */
export function useDownloadResumePdf() {
  return useMutation({
    mutationFn: async ({
      resumeVersionId,
      jobTitle,
      company,
    }: {
      resumeVersionId: string;
      jobTitle: string | null;
      company: string | null;
    }) => {
      let rendered: RenderedDocument;
      try {
        rendered = await getRenderedDocument(resumeVersionId);
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) {
          rendered = await renderResumePdf(resumeVersionId);
        } else {
          throw error;
        }
      }
      const blob = await downloadDocument(rendered.id);
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = pdfFilename(jobTitle, company);
      link.click();
      URL.revokeObjectURL(url);
    },
  });
}
