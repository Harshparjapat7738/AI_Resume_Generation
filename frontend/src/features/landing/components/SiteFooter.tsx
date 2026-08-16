import { SparkleIcon } from './icons';

/** Every entry here is a real, working anchor into the page itself — this product has
 *  no marketing site beyond this one page yet, so linking further would be a dead link. */
const linkColumns = [
  {
    heading: 'Product',
    links: [
      { label: 'Features', href: '#features' },
      { label: 'How It Works', href: '#workflow' },
      { label: 'ATS Score', href: '#ats-score' },
    ],
  },
  {
    heading: 'Resources',
    links: [
      { label: 'Security', href: '#security' },
      { label: 'FAQ', href: '#faq' },
    ],
  },
];

/** No real page exists behind any of these yet — shown as plain labels rather than dead
 *  links, same honesty rule as everything else on this page (and the product itself). */
const companyLabels = ['About Us', 'Careers', 'Privacy Policy', 'Terms of Service'];

export function SiteFooter() {
  return (
    <footer className="border-t border-border bg-surface">
      <div className="mx-auto max-w-6xl px-6 py-14">
        <div className="grid gap-10 md:grid-cols-[1.3fr_1fr_1fr_1fr]">
          <div>
            <a href="#top" className="flex items-center gap-2">
              <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-linear-to-br from-brand to-brand-2 text-void">
                <SparkleIcon className="h-4 w-4" />
              </span>
              <span className="text-base font-semibold tracking-tight text-ink">
                CareerForge <span className="text-gradient-brand">AI</span>
              </span>
            </a>
            <p className="mt-4 max-w-xs text-sm leading-relaxed text-ink-faint">
              AI-powered copilot to help you apply smarter — grounded in your real
              experience, never invented.
            </p>
          </div>

          {linkColumns.map((column) => (
            <div key={column.heading}>
              <h3 className="text-sm font-semibold text-ink">{column.heading}</h3>
              <ul className="mt-4 space-y-3">
                {column.links.map((link) => (
                  <li key={link.label}>
                    <a
                      href={link.href}
                      className="text-sm text-ink-faint transition-colors hover:text-ink"
                    >
                      {link.label}
                    </a>
                  </li>
                ))}
              </ul>
            </div>
          ))}

          <div>
            <h3 className="text-sm font-semibold text-ink">Company</h3>
            <ul className="mt-4 space-y-3">
              {companyLabels.map((label) => (
                <li key={label} className="text-sm text-ink-faint">
                  {label}
                </li>
              ))}
            </ul>
          </div>
        </div>

        <div className="mt-12 flex flex-col gap-3 border-t border-border pt-6 text-xs text-ink-faint sm:flex-row sm:items-center sm:justify-between">
          <p>&copy; {new Date().getFullYear()} CareerForge AI. Proprietary. All rights reserved.</p>
          <p>Profile, JD analysis, grounded generation and ATS scoring are live. Documents are next.</p>
        </div>
      </div>
    </footer>
  );
}
