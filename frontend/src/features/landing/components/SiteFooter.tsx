import { SparkleIcon } from './icons';

const columns = [
  {
    heading: 'Product',
    links: ['Profile & evidence', 'JD analysis', 'Grounded generation', 'ATS score'],
  },
  {
    heading: 'Trust',
    links: ['Security', 'Private storage', 'Gmail drafts, never sends'],
  },
];

export function SiteFooter() {
  return (
    <footer className="border-t border-border bg-surface">
      <div className="mx-auto max-w-6xl px-6 py-14">
        <div className="grid gap-10 md:grid-cols-[1.4fr_1fr_1fr]">
          <div>
            <div className="flex items-center gap-2">
              <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-surface-2 text-ember-soft ring-1 ring-border-strong">
                <SparkleIcon className="h-4 w-4" />
              </span>
              <span className="text-base font-semibold tracking-tight text-ink">
                CareerForge <span className="text-gradient">AI</span>
              </span>
            </div>
            <p className="mt-4 max-w-xs text-sm leading-relaxed text-ink-faint">
              Turn one verified professional profile into a job-specific application that is
              relevant, ATS-friendly, explainable, and grounded in your real experience.
            </p>
          </div>

          {columns.map((column) => (
            <div key={column.heading}>
              <h3 className="text-sm font-medium text-ink">{column.heading}</h3>
              <ul className="mt-4 space-y-3">
                {column.links.map((link) => (
                  <li key={link} className="text-sm text-ink-faint">
                    {link}
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>

        <div className="mt-12 flex flex-col gap-3 border-t border-border pt-6 text-xs text-ink-faint sm:flex-row sm:items-center sm:justify-between">
          <p>&copy; {new Date().getFullYear()} CareerForge AI. Proprietary. All rights reserved.</p>
          <p>Profile, JD analysis and grounded generation are live. ATS scoring and documents are next.</p>
        </div>
      </div>
    </footer>
  );
}
