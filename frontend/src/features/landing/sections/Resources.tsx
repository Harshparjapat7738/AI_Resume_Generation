import { Reveal } from '../components/Reveal';
import { ArrowRightIcon, ChecklistIcon, DatabaseIcon, GaugeIcon, ShieldCheckIcon } from '../components/icons';
import { resourceTeasers } from '../content';

const icons = [ShieldCheckIcon, GaugeIcon, DatabaseIcon, ChecklistIcon];

/**
 * "Guides and tips" (redesign brief §8). This product has no blog yet (see content.ts's
 * own doc comment) — every card here is a short, real, honest answer, and "Read more"
 * scrolls to the FAQ section immediately below, which carries the full answer, rather
 * than linking out to an article that doesn't exist.
 */
export function Resources() {
  return (
    <section className="border-t border-border bg-surface py-20 sm:py-24">
      <div className="mx-auto max-w-6xl px-6">
        <Reveal className="mx-auto max-w-2xl text-center">
          <p className="text-sm font-semibold uppercase tracking-wide text-brand">Resources</p>
          <h2 className="mt-3 text-3xl font-semibold tracking-tight text-ink sm:text-4xl">
            Answers, before you ask
          </h2>
        </Reveal>

        <Reveal stagger className="mt-14 grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
          {resourceTeasers.map((item, i) => {
            const Icon = icons[i % icons.length]!;
            return (
              <a
                key={item.title}
                href="#faq"
                className="group flex flex-col rounded-2xl border border-border bg-void p-6 transition-all duration-300 hover:-translate-y-1 hover:border-brand-soft hover:shadow-xl hover:shadow-brand/10"
              >
                <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-linear-to-br from-brand/15 to-brand-2/15 text-brand">
                  <Icon className="h-5 w-5" />
                </span>
                <p className="mt-4 text-xs font-medium uppercase tracking-wide text-ink-faint">
                  {item.category}
                </p>
                <h3 className="mt-1.5 text-sm font-semibold leading-snug text-ink">{item.title}</h3>
                <p className="mt-2 flex-1 text-sm leading-relaxed text-ink-faint">
                  {item.description}
                </p>
                <span className="mt-4 inline-flex items-center gap-1.5 text-sm font-medium text-brand">
                  Read more
                  <ArrowRightIcon className="h-3.5 w-3.5 transition-transform group-hover:translate-x-0.5" />
                </span>
              </a>
            );
          })}
        </Reveal>
      </div>
    </section>
  );
}
