import { Reveal } from '../components/Reveal';
import { CountUp } from '../components/CountUp';
import { stats } from '../content';

/**
 * "By the numbers" (redesign brief §6) — four real facts about the system itself (see
 * content.ts's own doc comment for sources), not usage/social-proof figures the product
 * has none of yet. Count-up plays once per scroll-into-view, same as every other reveal
 * on this page.
 */
export function Stats() {
  return (
    <section className="border-t border-border bg-void py-16 sm:py-20">
      <div className="mx-auto max-w-6xl px-6">
        <Reveal stagger className="grid grid-cols-2 gap-6 lg:grid-cols-4">
          {stats.map((stat) => (
            <div
              key={stat.label}
              className="rounded-2xl border border-border bg-surface p-6 text-center transition-colors hover:border-brand-soft"
            >
              <p className="text-3xl font-semibold tracking-tight text-ink sm:text-4xl">
                <CountUp value={stat.value} suffix={stat.suffix} className="text-gradient-brand" />
              </p>
              <p className="mt-2 text-sm leading-snug text-ink-faint">{stat.label}</p>
            </div>
          ))}
        </Reveal>
      </div>
    </section>
  );
}
