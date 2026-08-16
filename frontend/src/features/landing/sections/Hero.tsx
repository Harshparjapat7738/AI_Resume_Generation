import { Link } from 'react-router-dom';
import { useSession } from '@/services/session';
import { Reveal } from '../components/Reveal';
import { ArrowRightIcon, GaugeIcon, LockIcon, PlayIcon, ShieldCheckIcon } from '../components/icons';
import { HeroIllustration } from '../components/HeroIllustration';

const trustChips = [
  { icon: ShieldCheckIcon, label: 'Grounded by evidence' },
  { icon: GaugeIcon, label: 'Explainable ATS score' },
  { icon: LockIcon, label: 'Private by default' },
];

export function Hero() {
  const { data: user } = useSession();

  return (
    <section className="relative overflow-hidden pt-16 pb-20 sm:pt-24 sm:pb-28">
      <div aria-hidden="true" className="absolute inset-0 bg-brand-grid bg-brand-glow" />
      <div className="relative mx-auto grid max-w-6xl items-center gap-16 px-6 lg:grid-cols-[1.05fr_0.95fr]">
        <div>
          <Reveal>
            <span className="inline-flex items-center gap-2 rounded-full border border-border-strong bg-surface px-3 py-1 text-xs text-ink-muted">
              <span className="h-1.5 w-1.5 rounded-full bg-mint" />
              AI-Powered Application Copilot
            </span>
          </Reveal>

          <Reveal delay={0.08}>
            <h1 className="mt-6 text-4xl font-semibold leading-[1.08] tracking-tight text-ink sm:text-5xl lg:text-[3.4rem]">
              Smarter applications.
              <br />
              <span className="text-gradient-brand">Grounded in truth.</span>
            </h1>
          </Reveal>

          <Reveal delay={0.16}>
            <p className="mt-6 max-w-lg text-lg leading-relaxed text-ink-muted">
              CareerForge AI turns your real experience into a job-specific resume, cover
              letter and email — scored against the JD, with nothing invented.
            </p>
          </Reveal>

          <Reveal delay={0.24}>
            <div className="mt-9 flex flex-col gap-3 sm:flex-row sm:items-center">
              <Link
                to="/generate"
                className="group inline-flex items-center justify-center gap-2 rounded-full bg-linear-to-r from-brand to-brand-2 px-6 py-3 text-sm font-semibold text-void shadow-lg shadow-brand/20 transition-transform hover:-translate-y-0.5"
              >
                {user ? 'Generate a resume' : 'Get Started Free'}
                <ArrowRightIcon className="h-4 w-4 transition-transform group-hover:translate-x-0.5" />
              </Link>
              <a
                href="#workflow"
                className="inline-flex items-center justify-center gap-2 rounded-full border border-border-strong px-6 py-3 text-sm font-semibold text-ink transition-colors hover:border-brand-soft"
              >
                <PlayIcon className="h-4 w-4 text-brand" />
                See How It Works
              </a>
            </div>
          </Reveal>

          <Reveal stagger delay={0.3}>
            <div className="mt-10 flex flex-wrap gap-x-6 gap-y-3">
              {trustChips.map((chip) => (
                <div key={chip.label} className="flex items-center gap-2 text-sm text-ink-faint">
                  <chip.icon className="h-4 w-4 text-brand" />
                  {chip.label}
                </div>
              ))}
            </div>
          </Reveal>
        </div>

        <Reveal delay={0.2} className="relative">
          <HeroIllustration />
        </Reveal>
      </div>
    </section>
  );
}
