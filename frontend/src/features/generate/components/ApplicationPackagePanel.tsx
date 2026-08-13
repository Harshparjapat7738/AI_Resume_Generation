const PACKAGE_ITEMS = [
  { label: 'Resume', detail: 'Built from your selected template above.' },
  { label: 'Cover letter', detail: 'Grounded in the same profile evidence as your resume.' },
  { label: 'Application email', detail: 'A subject and body ready to send alongside them.' },
  { label: 'ATS & job-fit analysis', detail: 'Computed right after the resume is generated.' },
] as const;

/**
 * "Application package" summary for GENERATE_ALL (redesign spec &sect;13/15) — everything one
 * `Application` aggregate will hold once generation completes. Deliberately phrased as "included
 * in this run", not "✓ Ready", since nothing has been generated yet at review time; claiming
 * readiness before the fact would misstate what ATS/job-fit analysis actually needs (a generated
 * resume version — see assessment-service).
 */
export function ApplicationPackagePanel() {
  return (
    <div className="rounded-2xl border border-border bg-surface p-6 sm:p-8">
      <h2 className="text-base font-semibold text-ink">Application package</h2>
      <p className="mt-1 text-xs text-ink-faint">
        One application, generated together — resume, cover letter, email and analysis all share the same
        applicationId.
      </p>
      <ul className="mt-5 grid gap-3 sm:grid-cols-2">
        {PACKAGE_ITEMS.map((item) => (
          <li key={item.label} className="flex items-start gap-2.5 rounded-xl border border-border bg-void px-4 py-3">
            <span className="mt-0.5 text-mint" aria-hidden="true">
              ✓
            </span>
            <div>
              <p className="text-sm font-medium text-ink">{item.label}</p>
              <p className="mt-0.5 text-xs text-ink-faint">{item.detail}</p>
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}
