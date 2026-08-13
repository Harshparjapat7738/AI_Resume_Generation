import type { Assessment } from '@/services/assessmentApi';
import { ScoreCard } from './ScoreCard';

/** The 4-card score row (point 5 of the redesign spec) — a thin responsive-grid wrapper around
 *  the existing `ScoreCard` (unchanged, also used by AllResultPage). Desktop: 4 across.
 *  Tablet: 2×2. Mobile: 1 column. */
export function ScoreOverview({ assessment, skillsMatch }: { assessment: Assessment; skillsMatch: number | null }) {
  return (
    <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-4">
      <ScoreCard label="ATS Compatibility" score={assessment.atsScore} />
      <ScoreCard label="Job Description Match" score={assessment.compatibilityScore * 100} />
      <ScoreCard label="Keyword Match" score={assessment.keywordMatch * 100} />
      {skillsMatch !== null ? (
        <ScoreCard label="Skills Match" score={skillsMatch * 100} />
      ) : (
        <ScoreCard label="Estimated JD Description Match" score={assessment.compatibilityScore * 100} />
      )}
    </div>
  );
}
