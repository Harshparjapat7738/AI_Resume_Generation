import { Reveal } from '../components/Reveal';
import { DocumentIcon, EyeOffIcon, GaugeIcon } from '../components/icons';

const pains = [
  {
    icon: GaugeIcon,
    label: 'Filtered before a human ever looks',
    detail:
      'Applicant tracking systems reject formatting and structure before content is judged at all.',
  },
  {
    icon: DocumentIcon,
    label: 'Rewritten from scratch, every time',
    detail: 'Tailoring the same experience to each posting by hand doesn’t scale past a few applications.',
  },
  {
    icon: EyeOffIcon,
    label: "Invented facts don't survive an interview",
    detail:
      "Most AI resume tools will happily add a metric or a technology you've never touched — CareerForge AI won't.",
  },
];

export function Problem() {
  return (
    <section id="problem" className="scroll-mt-20 border-t border-border bg-void py-24">
      <div className="mx-auto max-w-6xl px-6">
        <Reveal className="max-w-2xl">
          <p className="text-sm font-medium uppercase tracking-wide text-ember-soft">The problem</p>
          <h2 className="mt-3 text-3xl font-semibold tracking-tight text-ink sm:text-4xl">
            Generic resumes lose to the filter. Fabricated ones lose the interview.
          </h2>
          <p className="mt-4 text-lg leading-relaxed text-ink-muted">
            Tailoring by hand doesn't scale, and most "AI resume builders" solve that by
            inventing facts instead. Both paths end the same way.
          </p>
        </Reveal>

        <Reveal stagger className="mt-14 grid gap-6 sm:grid-cols-3">
          {pains.map((pain) => (
            <div
              key={pain.label}
              className="rounded-2xl border border-border bg-surface p-6 transition-colors hover:border-border-strong"
            >
              <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-surface-2 text-ember-soft ring-1 ring-border-strong">
                <pain.icon className="h-5 w-5" />
              </span>
              <p className="mt-4 text-sm font-medium text-ink">{pain.label}</p>
              <p className="mt-2 text-sm leading-relaxed text-ink-faint">{pain.detail}</p>
            </div>
          ))}
        </Reveal>
      </div>
    </section>
  );
}
