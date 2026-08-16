import { Reveal } from '../components/Reveal';
import {
  CheckIcon,
  DocumentIcon,
  GaugeIcon,
  SendIcon,
  ShieldCheckIcon,
  SparkleIcon,
} from '../components/icons';
import { benefits } from '../content';

const icons = [SparkleIcon, CheckIcon, GaugeIcon, ShieldCheckIcon, DocumentIcon, SendIcon];

export function Benefits() {
  return (
    <section id="features" className="scroll-mt-20 border-t border-border bg-void py-20 sm:py-24">
      <div className="mx-auto max-w-6xl px-6">
        <Reveal className="mx-auto max-w-2xl text-center">
          <p className="text-sm font-semibold uppercase tracking-wide text-brand">
            Why it's different
          </p>
          <h2 className="mt-3 text-3xl font-semibold tracking-tight text-ink sm:text-4xl">
            Everything you need to apply with confidence
          </h2>
        </Reveal>

        <Reveal stagger className="mt-14 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {benefits.map((benefit, i) => {
            const Icon = icons[i % icons.length]!;
            return (
              <div
                key={benefit.title}
                className="group rounded-2xl border border-border bg-surface p-6 transition-all duration-300 hover:-translate-y-1.5 hover:border-brand-soft hover:shadow-xl hover:shadow-brand/10"
              >
                <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-linear-to-br from-brand/15 to-brand-2/15 text-brand transition-transform duration-300 group-hover:scale-110">
                  <Icon className="h-5 w-5" />
                </span>
                <h3 className="mt-4 text-base font-semibold text-ink">{benefit.title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-ink-faint">
                  {benefit.description}
                </p>
              </div>
            );
          })}
        </Reveal>
      </div>
    </section>
  );
}
