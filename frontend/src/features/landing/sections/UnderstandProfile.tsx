import { Reveal } from '../components/Reveal';
import { GaugeIcon, SearchIcon } from '../components/icons';
import { WorkspaceIllustration } from '../components/WorkspaceIllustration';
import { understandSteps } from '../content';

const badges = [
  { icon: SearchIcon, label: 'JD analysed', position: 'top-left' as const },
  { icon: GaugeIcon, label: 'Score explained', position: 'bottom-right' as const },
];

/** "Everything starts with understanding your profile" (redesign brief §9). */
export function UnderstandProfile() {
  return (
    <section className="border-t border-border bg-void py-20 sm:py-24">
      <div className="mx-auto max-w-6xl px-6">
        <div className="grid gap-14 lg:grid-cols-2 lg:items-center">
          <Reveal className="order-2 lg:order-1">
            <WorkspaceIllustration badges={badges} />
          </Reveal>

          <Reveal delay={0.1} className="order-1 lg:order-2">
            <h2 className="text-3xl font-semibold tracking-tight text-ink sm:text-4xl">
              Everything starts with understanding your profile.
            </h2>
            <p className="mt-4 text-ink-muted">
              No generation happens until your real experience and the job's real
              requirements are both on the table.
            </p>

            <ol className="mt-8 space-y-5">
              {understandSteps.map((step, i) => (
                <li key={step.title} className="flex items-start gap-4">
                  <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-brand/10 text-sm font-semibold text-brand">
                    {i + 1}
                  </span>
                  <div>
                    <p className="text-sm font-semibold text-ink">{step.title}</p>
                    <p className="mt-1 text-sm leading-relaxed text-ink-faint">{step.description}</p>
                  </div>
                </li>
              ))}
            </ol>
          </Reveal>
        </div>
      </div>
    </section>
  );
}
