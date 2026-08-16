import { Reveal } from '../components/Reveal';
import { CheckIcon, DocumentIcon, QuoteIcon, ShieldCheckIcon } from '../components/icons';
import { WorkspaceIllustration } from '../components/WorkspaceIllustration';
import { prepareBullets } from '../content';

const badges = [
  { icon: DocumentIcon, label: 'Resume ready', position: 'top-right' as const },
  { icon: CheckIcon, label: 'Evidence linked', position: 'bottom-left' as const },
];

export function Prepare() {
  return (
    <section className="scroll-mt-20 border-t border-border bg-surface py-20 sm:py-24">
      <div className="mx-auto max-w-6xl px-6">
        <div className="grid gap-12 lg:grid-cols-[0.8fr_0.9fr_0.8fr] lg:items-center lg:gap-8">
          <Reveal>
            <h2 className="text-3xl font-semibold tracking-tight text-ink sm:text-4xl">
              Prepare better.
              <br />
              <span className="text-gradient-brand">Apply with confidence.</span>
            </h2>
            <p className="mt-4 text-ink-muted">
              CareerForge AI gives you a defensible application, so you can focus on the role
              — not on whether your resume will hold up under questioning.
            </p>
            <ul className="mt-6 space-y-3">
              {prepareBullets.map((bullet) => (
                <li key={bullet} className="flex items-start gap-2.5 text-sm text-ink-muted">
                  <span className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-mint/10 text-mint">
                    <CheckIcon className="h-3 w-3" />
                  </span>
                  {bullet}
                </li>
              ))}
            </ul>
          </Reveal>

          <Reveal delay={0.1}>
            <div className="rounded-2xl border border-border bg-void p-7 shadow-lg shadow-black/5">
              <span className="flex h-9 w-9 items-center justify-center rounded-full bg-brand/10 text-brand">
                <QuoteIcon className="h-4 w-4" />
              </span>
              <p className="mt-4 text-base leading-relaxed text-ink">
                "Every sentence traces back to something you actually did. If it can't be
                traced, it doesn't get written."
              </p>
              <div className="mt-5 flex items-center gap-2 border-t border-border pt-4 text-xs text-ink-faint">
                <ShieldCheckIcon className="h-4 w-4 text-mint" />
                CareerForge AI's grounding rule — enforced in code, not just promised
              </div>
            </div>
          </Reveal>

          <Reveal delay={0.2} className="hidden lg:block">
            <WorkspaceIllustration badges={badges} />
          </Reveal>
        </div>
      </div>
    </section>
  );
}
