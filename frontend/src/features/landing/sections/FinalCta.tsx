import { Link } from 'react-router-dom';
import { useSession } from '@/services/session';
import { Reveal } from '../components/Reveal';
import { ArrowRightIcon } from '../components/icons';
import { HeroIllustration } from '../components/HeroIllustration';

export function FinalCta() {
  const { data: user } = useSession();

  return (
    <section className="relative overflow-hidden border-t border-border bg-cream py-20 sm:py-24">
      <div className="absolute inset-0 bg-brand-glow" aria-hidden="true" />
      <div className="relative mx-auto grid max-w-6xl items-center gap-12 px-6 lg:grid-cols-[1.1fr_0.9fr]">
        <Reveal>
          <h2 className="text-3xl font-semibold tracking-tight text-ink sm:text-4xl">
            Ready to apply with confidence?
          </h2>
          <p className="mt-4 max-w-md text-lg leading-relaxed text-ink-muted">
            One verified profile. Every job description. Nothing invented, nothing sent
            without your say-so.
          </p>

          <div className="mt-9 flex flex-col items-start gap-3 sm:flex-row">
            <Link
              to="/generate"
              className="group inline-flex items-center justify-center gap-2 rounded-full bg-linear-to-r from-brand to-brand-2 px-6 py-3 text-sm font-semibold text-void shadow-lg shadow-brand/20 transition-transform hover:-translate-y-0.5"
            >
              {user ? 'Generate a resume' : 'Get Started Free'}
              <ArrowRightIcon className="h-4 w-4 transition-transform group-hover:translate-x-0.5" />
            </Link>
          </div>
          <p className="mt-5 text-xs text-ink-faint">
            Live today: profile evidence, JD analysis, grounded generation and ATS scoring.
          </p>
        </Reveal>

        <Reveal delay={0.1} className="hidden lg:block">
          <HeroIllustration compact />
        </Reveal>
      </div>
    </section>
  );
}
