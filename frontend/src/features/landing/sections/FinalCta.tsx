import { Link } from 'react-router-dom';
import { Reveal } from '../components/Reveal';
import { ArrowRightIcon } from '../components/icons';
import { useSession } from '@/services/session';

export function FinalCta() {
  const { data: user } = useSession();

  return (
    <section className="relative overflow-hidden border-t border-border bg-void py-24">
      <div className="absolute inset-0 bg-forge-glow" aria-hidden="true" />
      <div className="relative mx-auto max-w-3xl px-6 text-center">
        <Reveal>
          <h2 className="text-3xl font-semibold tracking-tight text-ink sm:text-4xl">
            Build an application you can defend, line by line.
          </h2>
          <p className="mt-4 text-lg leading-relaxed text-ink-muted">
            One verified profile. Every job description. Nothing invented, nothing sent without
            your say-so.
          </p>
        </Reveal>

        <Reveal delay={0.1}>
          <div className="mt-9 flex flex-col items-center gap-3 sm:flex-row sm:justify-center">
            <Link
              to="/generate"
              className="group inline-flex items-center justify-center gap-2 rounded-full bg-linear-to-r from-ember-soft to-rose px-6 py-3 text-sm font-semibold text-void transition-transform hover:-translate-y-0.5"
            >
              {user ? 'Generate a resume' : 'Get started'}
              <ArrowRightIcon className="h-4 w-4 transition-transform group-hover:translate-x-0.5" />
            </Link>
          </div>
          <p className="mt-5 text-xs text-ink-faint">
            Live today: profile evidence, JD analysis and grounded generation. ATS scoring,
            document export and cover letters are on the way.
          </p>
        </Reveal>
      </div>
    </section>
  );
}
