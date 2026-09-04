import { Card } from '@/components/ui/Card';
import type { Assessment, AtsAssessment } from '@/services/assessmentApi';

/**
 * The three scores CareerForge can show for a job description regardless of whether the resume
 * PDF itself ever renders (ADR-040): ATS structural score, JD-match ("compatibility") score, and
 * the selection-readiness band. All three come from `assessment-service`, computed from the JD
 * optimization and the candidate's profile — never from a rendered document — so they exist
 * whether or not `generateResumePdf` on this page succeeds.
 *
 * <p>Deliberately tolerant of either input being missing or still loading: each stat renders its
 * own "—" placeholder rather than the whole card disappearing, since a failure to fetch one score
 * should never hide the other two.
 */

const BAND_COPY: Record<string, { label: string; hint: string; tone: 'good' | 'warn' | 'bad' }> = {
  STRONG: { label: 'Strong', hint: 'You look like a strong fit for this role.', tone: 'good' },
  COMPETITIVE: { label: 'Competitive', hint: 'A solid, competitive candidate for this role.', tone: 'good' },
  STRETCH: { label: 'Stretch', hint: 'A reach for this role, but worth applying.', tone: 'warn' },
  WEAK_FIT: { label: 'Weak fit', hint: 'Significant gaps versus this role today.', tone: 'bad' },
};

const TONE_STYLES = {
  good: 'text-mint',
  warn: 'text-ember-soft',
  bad: 'text-rose',
  neutral: 'text-ink-faint',
} as const;

function Stat({
  label,
  value,
  detail,
  tone = 'neutral',
  isLoading,
}: {
  label: string;
  value: string | null;
  detail?: string;
  tone?: 'good' | 'warn' | 'bad' | 'neutral';
  isLoading: boolean;
}) {
  return (
    <div className="rounded-xl border border-border bg-surface px-4 py-3.5">
      <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">{label}</p>
      <p className={`mt-1.5 text-2xl font-semibold tabular-nums ${value ? TONE_STYLES[tone] : 'text-ink-faint'}`}>
        {isLoading ? '…' : value ?? '—'}
      </p>
      {detail && <p className="mt-1 text-xs text-ink-muted">{detail}</p>}
    </div>
  );
}

export function ScoresCard({
  ats,
  atsLoading,
  fit,
  fitLoading,
}: {
  ats: AtsAssessment | undefined;
  atsLoading: boolean;
  fit: Assessment | undefined;
  fitLoading: boolean;
}) {
  const band = fit ? BAND_COPY[fit.readinessBand] : undefined;

  return (
    <Card>
      <h2 className="text-sm font-semibold text-ink">Your scores</h2>
      <p className="mt-1.5 text-sm text-ink-muted">
        Computed from your verified profile and this job description — available even if resume
        PDF generation below fails, so you always know where you stand before deciding whether to
        proceed with your existing resume or template.
      </p>
      <div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-3">
        <Stat
          label="ATS score"
          value={ats ? `${ats.atsScore.toFixed(1)}` : null}
          detail="How parseable your cited resume content is for applicant-tracking software."
          tone={ats ? (ats.atsScore >= 85 ? 'good' : ats.atsScore >= 60 ? 'warn' : 'bad') : 'neutral'}
          isLoading={atsLoading}
        />
        <Stat
          label="JD match score"
          value={fit ? `${Math.round(fit.compatibilityScore * 100)}%` : null}
          detail="How well your evidence covers this job description's requirements."
          tone={fit ? (fit.compatibilityScore >= 0.65 ? 'good' : fit.compatibilityScore >= 0.4 ? 'warn' : 'bad') : 'neutral'}
          isLoading={fitLoading}
        />
        <Stat
          label="Chances of selection"
          value={band ? band.label : fit ? fit.readinessBand : null}
          detail={band?.hint}
          tone={band?.tone ?? 'neutral'}
          isLoading={fitLoading}
        />
      </div>
    </Card>
  );
}
