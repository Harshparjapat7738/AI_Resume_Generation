import { useEffect, useState } from 'react';
import { SparkleIcon } from './icons';

const navLinks = [
  { href: '#problem', label: 'Problem' },
  { href: '#workflow', label: 'How it works' },
  { href: '#ats-score', label: 'ATS score' },
  { href: '#security', label: 'Security' },
  { href: '#faq', label: 'FAQ' },
];

export function SiteHeader() {
  const [scrolled, setScrolled] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  return (
    <header
      className={`sticky top-0 z-50 border-b transition-colors duration-300 ${
        scrolled
          ? 'border-border bg-void/80 backdrop-blur-md'
          : 'border-transparent bg-transparent'
      }`}
    >
      <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
        <a href="#top" className="flex items-center gap-2">
          <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-surface-2 text-ember-soft ring-1 ring-border-strong">
            <SparkleIcon className="h-4 w-4" />
          </span>
          <span className="text-base font-semibold tracking-tight text-ink">
            CareerForge <span className="text-gradient">AI</span>
          </span>
        </a>

        <nav className="hidden items-center gap-8 md:flex">
          {navLinks.map((link) => (
            <a
              key={link.href}
              href={link.href}
              className="text-sm text-ink-muted transition-colors hover:text-ink"
            >
              {link.label}
            </a>
          ))}
        </nav>

        <a
          href="#workflow"
          className="hidden rounded-full bg-ink px-4 py-2 text-sm font-medium text-void transition-transform hover:-translate-y-0.5 md:inline-block"
        >
          See how it works
        </a>

        <button
          type="button"
          onClick={() => setMenuOpen((open) => !open)}
          className="inline-flex h-9 w-9 items-center justify-center rounded-lg border border-border text-ink md:hidden"
          aria-expanded={menuOpen}
          aria-label="Toggle navigation menu"
        >
          <span className="relative block h-3.5 w-4">
            <span
              className={`absolute left-0 h-px w-4 bg-current transition-transform duration-200 ${
                menuOpen ? 'top-1.5 rotate-45' : 'top-0'
              }`}
            />
            <span
              className={`absolute left-0 top-1.5 h-px w-4 bg-current transition-opacity duration-200 ${
                menuOpen ? 'opacity-0' : 'opacity-100'
              }`}
            />
            <span
              className={`absolute left-0 h-px w-4 bg-current transition-transform duration-200 ${
                menuOpen ? 'top-1.5 -rotate-45' : 'top-3'
              }`}
            />
          </span>
        </button>
      </div>

      {menuOpen && (
        <nav className="border-t border-border bg-void px-6 py-4 md:hidden">
          <ul className="flex flex-col gap-4">
            {navLinks.map((link) => (
              <li key={link.href}>
                <a
                  href={link.href}
                  onClick={() => setMenuOpen(false)}
                  className="block text-sm text-ink-muted hover:text-ink"
                >
                  {link.label}
                </a>
              </li>
            ))}
            <li>
              <a
                href="#workflow"
                onClick={() => setMenuOpen(false)}
                className="mt-1 inline-block rounded-full bg-ink px-4 py-2 text-sm font-medium text-void"
              >
                See how it works
              </a>
            </li>
          </ul>
        </nav>
      )}
    </header>
  );
}
