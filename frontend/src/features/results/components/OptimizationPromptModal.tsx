import { useEffect, useMemo, useRef } from 'react';
import { Button } from '@/components/ui/Button';
import { showToast } from '@/components/ui/toast';
import { CopyIcon, DownloadIcon, XIcon } from '@/features/dashboard/icons';
import type { OptimizationData } from '@/services/jdApi';
import type { TemplateResponse } from '@/services/templateApi';

/**
 * The external-generation prompt, assembled entirely client-side from data already on the page.
 * Deterministic string concatenation — no API call, no AI call, nothing fetched.
 *
 * <p>Carries only the optimization result: the JD terms that matter, which of them the user's
 * own evidence backs, what is missing, and what to lead with. No key, no internal prompt, no
 * service URL, no database id — {@code JdOptimization.id} and the JD's own id are deliberately
 * left out, since an external platform has no use for either.
 *
 * <p>The rules block is the load-bearing part: the whole product promise is that a gap stays a
 * gap, so the prompt names the missing requirements explicitly and forbids claiming them rather
 * than trusting the external model to infer that from silence.
 *
 * <p>{@code selectedTemplate} (ADR-034) is the one addition this function makes beyond the
 * optimization data itself — a name/type reference to a template already sitting in the user's
 * own "My Templates" library, injected as one extra block so the external tool knows which
 * layout to apply the drafted content to. Nothing else changes, and omitting it (the default)
 * reproduces the exact prompt this function always produced.
 */
export function buildOptimizationPrompt(
  data: OptimizationData,
  selectedTemplate?: Pick<TemplateResponse, 'name' | 'fileName' | 'documentType'> | null,
): string {
  const missing = data.missingRequirements ?? [];
  const supported = (data.keywords ?? []).filter((k) => k.evidenceIds.length > 0).map((k) => k.term);
  const unsupported = (data.keywords ?? []).filter((k) => k.evidenceIds.length === 0).map((k) => k.term);
  const templateBlock = selectedTemplate
    ? `\nSELECTED TEMPLATE (from my saved template library — apply the drafted content to this
template's existing layout and formatting, do not design a new one):
${selectedTemplate.name} (${selectedTemplate.fileName}, ${selectedTemplate.documentType})
`
    : '';

  return `You are helping me tailor my application for a specific job.

Use the supplied JD optimization data to help create my Resume and/or Cover Letter.

Use only verified candidate information.

Do not invent:
- experience
- companies
- dates
- metrics
- skills
- certifications
- achievements

Prioritize required JD keywords.
Use matched evidence.
Do not claim missing requirements.

TARGET ROLE:
${data.targetRole ?? '(not stated)'}${data.targetCompany ? ` at ${data.targetCompany}` : ''}

KEYWORDS MY PROFILE SUPPORTS (safe to emphasise):
${supported.length > 0 ? supported.join(', ') : '(none)'}

KEYWORDS MY PROFILE DOES NOT SUPPORT (never claim these):
${unsupported.length > 0 ? unsupported.join(', ') : '(none)'}

REQUIREMENTS I CANNOT EVIDENCE (${missing.length}) — do not imply, approximate or borrow these:
${missing.length > 0 ? missing.map((m) => `- ${m.requirementId}${m.note ? `: ${m.note}` : ''}`).join('\n') : '(none)'}

FULL JD OPTIMIZATION DATA:
${JSON.stringify(data, null, 2)}
${templateBlock}
TASK:
Using only the evidence referenced above, draft the document I ask for next. Where the data
marks a requirement as missing, leave it out entirely rather than substituting something
similar. Keep the result professional and ATS-friendly.
`;
}

/**
 * Read-and-copy view for the external prompt. A plain focus-trapped overlay rather than a new
 * dependency — the app has no modal primitive, and `ConfirmDialog` is shaped for a yes/no
 * decision, not a long scrollable body.
 *
 * <p>When {@code selectedTemplate} is supplied (ADR-034), this doubles as the "Create with
 * ChatGPT" handoff surface: a status summary confirming what's about to leave the app, plus a
 * "Download Template" action alongside the prompt's own Copy — see section 10/11 of the My
 * Templates feature. With no template selected it behaves exactly as it always did.
 */
export function OptimizationPromptModal({
  data,
  selectedTemplate,
  onDownloadTemplate,
  onClose,
}: {
  data: OptimizationData;
  /** The user's currently-chosen saved template, or `null`/omitted when none is selected yet —
   *  see OptimizationResultPage.tsx's "Choose your template" section. */
  selectedTemplate?: TemplateResponse | null;
  /** Downloads the selected template's original file — only ever called when one is selected,
   *  since the button that triggers it doesn't render otherwise. */
  onDownloadTemplate?: () => void;
  onClose: () => void;
}) {
  const prompt = useMemo(() => buildOptimizationPrompt(data, selectedTemplate), [data, selectedTemplate]);
  const closeRef = useRef<HTMLButtonElement>(null);
  const isHandoff = Boolean(selectedTemplate);

  useEffect(() => {
    closeRef.current?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(prompt);
      showToast('Prompt copied to clipboard.');
    } catch {
      showToast("Couldn't copy — select the text and copy manually.");
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-void/80 p-4"
      role="dialog"
      aria-modal="true"
      aria-label="External AI prompt"
      onClick={onClose}
    >
      <div
        className="flex max-h-[85vh] w-full max-w-3xl flex-col rounded-2xl border border-border bg-surface p-5 sm:p-6"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 className="text-lg font-semibold text-ink">External AI prompt</h2>
            <p className="mt-1 text-sm text-ink-muted">
              Paste this into ChatGPT, Gemini, Claude or any document tool to draft your resume or
              cover letter from this optimization.
            </p>
          </div>
          <button
            ref={closeRef}
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="rounded-full p-1.5 text-ink-faint transition-colors hover:text-ink"
          >
            <XIcon className="h-5 w-5" />
          </button>
        </div>

        {isHandoff && selectedTemplate && (
          <dl className="mt-4 grid grid-cols-1 gap-x-6 gap-y-2 rounded-xl border border-border bg-surface-2 p-4 text-sm sm:grid-cols-3">
            <div>
              <dt className="text-xs font-medium uppercase tracking-wide text-ink-faint">Selected template</dt>
              <dd className="mt-0.5 truncate text-ink">{selectedTemplate.fileName}</dd>
            </div>
            <div>
              <dt className="text-xs font-medium uppercase tracking-wide text-ink-faint">JD optimization</dt>
              <dd className="mt-0.5 text-mint">Ready</dd>
            </div>
            <div>
              <dt className="text-xs font-medium uppercase tracking-wide text-ink-faint">Generation prompt</dt>
              <dd className="mt-0.5 text-mint">Ready</dd>
            </div>
          </dl>
        )}

        <pre className="mt-4 flex-1 overflow-auto rounded-lg border border-border bg-surface-2 p-3 font-mono text-xs leading-relaxed text-ink">
          {prompt}
        </pre>

        <p className="mt-3 text-xs text-ink-faint">
          This prompt contains your profile and job-related data. Review it before sharing it with
          another platform.
        </p>

        <div className="mt-4 flex flex-col gap-2 sm:flex-row">
          <Button variant="primary" onClick={copy}>
            <CopyIcon className="h-4 w-4" />
            Copy
          </Button>
          {isHandoff && onDownloadTemplate && (
            <Button variant="secondary" onClick={onDownloadTemplate}>
              <DownloadIcon className="h-4 w-4" />
              Download Template
            </Button>
          )}
          <Button variant="secondary" onClick={onClose}>
            Close
          </Button>
        </div>
      </div>
    </div>
  );
}
