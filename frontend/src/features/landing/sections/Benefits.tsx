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
    <section id="benefits" className="scroll-mt-20 border-t border-border bg-surface py-24">
      <div className="mx-auto max-w-6xl px-6">
        <Reveal className="max-w-2xl">
          <p className="text-sm font-medium uppercase tracking-wide text-ember-soft">
            Why it's different
          </p>
          <h2 className="mt-3 text-3xl font-semibold tracking-tight text-ink sm:text-4xl">
            Built to be trusted in an interview room.
          </h2>
        </Reveal>

        <Reveal stagger className="mt-14 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {benefits.map((benefit, i) => {
            const Icon = icons[i % icons.length]!;
            return (
              <div
                key={benefit.title}
                className="rounded-2xl border border-border bg-void p-6 transition-colors hover:border-border-strong"
              >
                <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-surface-2 text-ember-soft ring-1 ring-border-strong">
                  <Icon className="h-5 w-5" />
                </span>
                <h3 className="mt-4 text-base font-medium text-ink">{benefit.title}</h3>
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
