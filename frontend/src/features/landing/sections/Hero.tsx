import { Link } from 'react-router-dom';
import { Reveal } from '../components/Reveal';
import { ArrowRightIcon, CheckIcon, GaugeIcon, MailIcon, ShieldCheckIcon } from '../components/icons';
import { atsChecks } from '../content';
import { useSession } from '@/services/session';

const trustChips = [
  { icon: GaugeIcon, label: '10 deterministic ATS checks' },
  { icon: ShieldCheckIcon, label: 'Zero fabricated facts' },
  { icon: MailIcon, label: 'Drafts only, never auto-sent' },
];

export function Hero() {
  const { data: user } = useSession();

  return (
    <section className="relative overflow-hidden pt-20 pb-24 sm:pt-28 sm:pb-32">
      <div aria-hidden="true" className="absolute inset-0 bg-forge-grid bg-forge-glow" />
      <div className="relative mx-auto grid max-w-6xl items-center gap-16 px-6 lg:grid-cols-[1.1fr_0.9fr]">
        <div>
          <Reveal>
            <span className="inline-flex items-center gap-2 rounded-full border border-border-strong bg-surface px-3 py-1 text-xs text-ink-muted">
              <span className="h-1.5 w-1.5 rounded-full bg-mint" />
              Evidence-grounded generation is live — try it free
            </span>
          </Reveal>

          <Reveal delay={0.08}>
            <h1 className="mt-6 text-4xl font-semibold leading-[1.08] tracking-tight text-ink sm:text-5xl lg:text-6xl">
              Every application, <span className="text-gradient">grounded in evidence</span> —
              never invention.
            </h1>
          </Reveal>

          <Reveal delay={0.16}>
            <p className="mt-6 max-w-xl text-lg leading-relaxed text-ink-muted">
              CareerForge AI stores your verified professional experience and turns a job
              description into a tailored resume, cover letter and application email — then
              scores the result against that JD and explains exactly what matches, and what's
              missing.
            </p>
          </Reveal>

          <Reveal delay={0.24}>
            <div className="mt-9 flex flex-col gap-3 sm:flex-row sm:items-center">
              <Link
                to="/generate"
                className="group inline-flex items-center justify-center gap-2 rounded-full bg-linear-to-r from-ember-soft to-rose px-6 py-3 text-sm font-semibold text-void transition-transform hover:-translate-y-0.5"
              >
                {user ? 'Generate a resume' : 'Get started'}
                <ArrowRightIcon className="h-4 w-4 transition-transform group-hover:translate-x-0.5" />
              </Link>
              <a
                href="#benefits"
                className="inline-flex items-center justify-center gap-2 rounded-full border border-border-strong px-6 py-3 text-sm font-semibold text-ink transition-colors hover:border-ink-muted"
              >
                Why it's different
              </a>
            </div>
          </Reveal>

          <Reveal stagger delay={0.3}>
            <div className="mt-10 flex flex-wrap gap-x-6 gap-y-3">
              {trustChips.map((chip) => (
                <div key={chip.label} className="flex items-center gap-2 text-sm text-ink-faint">
                  <chip.icon className="h-4 w-4 text-ember-soft" />
                  {chip.label}
                </div>
              ))}
            </div>
          </Reveal>
        </div>

        <Reveal delay={0.2} className="relative">
          <div className="rounded-2xl border border-border bg-surface/80 p-6 shadow-2xl shadow-black/40 backdrop-blur">
            <div className="flex items-center justify-between">
              <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">
                Screening readiness
              </p>
              <span className="rounded-full bg-mint/10 px-2.5 py-1 text-xs font-semibold text-mint">
                STRONG
              </span>
            </div>
            <p className="mt-2 text-sm text-ink-muted">
              Coverage, seniority and recency all align with this JD's requirements.
            </p>

            <div className="mt-6 space-y-4">
              {atsChecks.slice(0, 4).map((check) => (
                <div key={check.label}>
                  <div className="flex items-center justify-between text-xs">
                    <span className="text-ink-muted">{check.label}</span>
                    <span className="font-medium text-ink">{check.score}</span>
                  </div>
                  <div className="mt-1.5 h-1.5 w-full overflow-hidden rounded-full bg-surface-2">
                    <div
                      className="h-full rounded-full bg-linear-to-r from-ember-soft to-rose"
                      style={{ width: `${check.score}%` }}
                    />
                  </div>
                </div>
              ))}
            </div>

            <div className="mt-6 flex items-center gap-2 rounded-xl border border-border bg-surface-2 px-3 py-2.5 text-xs text-ink-muted">
              <CheckIcon className="h-3.5 w-3.5 shrink-0 text-mint" />
              Every line traces to an evidence ID in your profile.
            </div>
          </div>

          <div
            aria-hidden="true"
            className="absolute -bottom-6 -right-6 -z-10 h-40 w-40 rounded-full bg-ember/20 blur-3xl"
          />
        </Reveal>
      </div>
    </section>
  );
}
