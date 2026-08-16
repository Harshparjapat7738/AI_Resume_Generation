import { Reveal } from '../components/Reveal';
import {
  DatabaseIcon,
  EyeOffIcon,
  GaugeIcon,
  LockIcon,
  MailIcon,
  ShieldIcon,
} from '../components/icons';
import { securityPoints } from '../content';

const icons = [ShieldIcon, LockIcon, DatabaseIcon, EyeOffIcon, GaugeIcon, MailIcon];

export function Security() {
  return (
    <section id="security" className="scroll-mt-20 border-t border-border bg-void py-20 sm:py-24">
      <div className="mx-auto max-w-6xl px-6">
        <Reveal className="mx-auto max-w-2xl text-center">
          <p className="text-sm font-semibold uppercase tracking-wide text-brand">Security</p>
          <h2 className="mt-3 text-3xl font-semibold tracking-tight text-ink sm:text-4xl">
            Private by default, verifiable by design
          </h2>
          <p className="mt-4 text-lg leading-relaxed text-ink-muted">
            The same rigor that keeps generated content honest is applied to how your data is
            stored, reached and shared — never further than it has to be.
          </p>
        </Reveal>

        <Reveal stagger className="mt-14 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {securityPoints.map((point, i) => {
            const Icon = icons[i % icons.length]!;
            return (
              <div
                key={point.title}
                className="rounded-2xl border border-border bg-surface p-6 transition-all duration-300 hover:-translate-y-1 hover:border-brand-soft hover:shadow-xl hover:shadow-brand/10"
              >
                <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-mint/10 text-mint">
                  <Icon className="h-5 w-5" />
                </span>
                <h3 className="mt-4 text-base font-semibold text-ink">{point.title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-ink-faint">{point.description}</p>
              </div>
            );
          })}
        </Reveal>
      </div>
    </section>
  );
}
