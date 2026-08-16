/**
 * Copy and structured content for the landing page sections. Kept out of JSX so
 * each section component stays focused on layout/animation. Every claim here
 * traces back to README.md / docs — the product doesn't get to invent facts about
 * user experience, and neither does its own marketing page.
 */

export interface WorkflowStep {
  index: string;
  title: string;
  description: string;
}

/** The four-stage visual journey (redesign brief §3, "How It Works"). Compresses the
 *  product's real five stages (see `docs/CODEBASE.md` §3) by folding scoring into the
 *  generation step — still every real stage, just grouped for a four-card layout. */
export const howItWorks: WorkflowStep[] = [
  {
    index: '01',
    title: 'Build your profile',
    description:
      'Add your real experience, education, skills and projects — each fact gets a stable evidence ID the rest of the pipeline can point back to.',
  },
  {
    index: '02',
    title: 'Add a job description',
    description:
      'Paste text, upload a PDF/DOCX, or supply a URL. You confirm the extracted JD and see it analysed before anything is generated.',
  },
  {
    index: '03',
    title: 'Generate & score',
    description:
      'A two-stage pipeline writes grounded content, then a deterministic ATS and JD-fit score explains exactly what matches.',
  },
  {
    index: '04',
    title: 'Apply with confidence',
    description:
      'A tailored resume, cover letter and email — plus a Gmail draft you review yourself. Nothing is ever sent on your behalf.',
  },
];

export interface AtsCheck {
  label: string;
  score: number;
}

/** The seven real, deterministic checks `assessment-service`'s `AtsScoringEngine` runs
 *  in Java against structured resume content — never asked of the LLM (ADR-014). Scores
 *  shown are one illustrative example, not a live/aggregate figure. */
export const atsChecks: AtsCheck[] = [
  { label: 'Contact & parse safety', score: 100 },
  { label: 'Professional summary', score: 90 },
  { label: 'Experience section', score: 95 },
  { label: 'Date consistency', score: 100 },
  { label: 'Bullet clarity', score: 82 },
  { label: 'Keyword match', score: 87 },
  { label: 'Grounding integrity', score: 100 },
];

/** The JD-fit dimensions `assessment-service`'s `JdFitScoringEngine` weights (coverage
 *  0.50, keyword 0.20, seniority 0.20, recency 0.10 — see `docs/CODEBASE.md` §2
 *  assessment-service) — a separate real score from the ATS checks above, shown together
 *  in the hero mockup the way the product's own result page shows both side by side. */
export const jdFitChecks: AtsCheck[] = [
  { label: 'Requirement coverage', score: 92 },
  { label: 'Keyword match', score: 87 },
  { label: 'Seniority alignment', score: 95 },
  { label: 'Recency', score: 78 },
];

export interface Benefit {
  title: string;
  description: string;
}

export const benefits: Benefit[] = [
  {
    title: 'Grounded generation',
    description:
      'The AI may select, rank, condense and rephrase facts you supplied. It never invents an employer, date, metric, technology, certification, project or achievement.',
  },
  {
    title: 'Traceable to the source',
    description:
      "Every generated statement traces to an evidence ID in your profile. Content that can't be traced is removed and reported as a gap, not smoothed over.",
  },
  {
    title: 'JD compatibility, explained',
    description:
      'Coverage, keyword match, seniority alignment and recency — each scored and traceable back to the requirement and the evidence behind it.',
  },
  {
    title: 'Honest screening readiness',
    description:
      'STRONG, COMPETITIVE, STRETCH or WEAK FIT, with the reason shown. No promise of a job, and no fabricated hiring probability.',
  },
  {
    title: 'ATS-safe documents',
    description:
      'Three single-column templates rendered deterministically to PDF and DOCX — built to parse cleanly, not just to look good.',
  },
  {
    title: 'Applications, tracked',
    description:
      'Cover letter, email and Gmail draft together with history and status — the draft is never auto-sent without your review.',
  },
];

export interface SecurityPoint {
  title: string;
  description: string;
}

export const securityPoints: SecurityPoint[] = [
  {
    title: 'One public entry point',
    description:
      'The gateway verifies every JWT, rate-limits and applies CORS. Business services publish no host port and are unreachable directly.',
  },
  {
    title: 'SSRF-hardened JD fetch',
    description:
      'Job description URLs are fetched through a client that blocks link-local and internal addresses before a request ever leaves the service.',
  },
  {
    title: 'Private by default',
    description:
      'Uploaded and generated files live in a private bucket and are never made public — not even the bucket console.',
  },
  {
    title: "404, never 403",
    description:
      "Another user's resource returns 404. The API never confirms that something exists before confirming it isn't yours.",
  },
  {
    title: 'Deterministic scoring',
    description:
      'ATS and JD-compatibility scores are computed in Java against explicit rules — never asked of the LLM, never a black box.',
  },
  {
    title: 'Drafts, not sends',
    description:
      'Gmail integration creates a draft. Nothing reaches an inbox without you opening Gmail and pressing send yourself.',
  },
];

export interface FaqItem {
  question: string;
  answer: string;
}

export const faqItems: FaqItem[] = [
  {
    question: "Will it ever invent experience I don't have?",
    answer:
      "No. The model may select, rank, condense and rephrase what you provide — it never invents an employer, date, metric, technology, certification, project or achievement. Anything that can't be traced to an evidence ID in your profile is removed and reported as a gap.",
  },
  {
    question: 'Does it promise me a job, or a hiring probability?',
    answer:
      "No. CareerForge AI doesn't promise a job and doesn't display a fabricated hiring probability. Screening readiness is shown as a band — STRONG, COMPETITIVE, STRETCH or WEAK FIT — with the reason behind it.",
  },
  {
    question: 'Will an email or application get sent without me seeing it?',
    answer:
      "Never. The Gmail integration only ever creates a draft. Sending is a deliberate action you take in your own inbox.",
  },
  {
    question: 'Where does my data live?',
    answer:
      'In MongoDB Atlas and a private object store, scoped per service. Uploaded and generated files are never public, and another user requesting your resource gets a 404, not a hint that it exists.',
  },
  {
    question: 'How is the ATS score actually computed?',
    answer:
      'By ten weighted checks running in Java — deterministic and explainable down to the sub-check. It is never a number the language model was asked to guess.',
  },
  {
    question: "What happens if a job needs a skill I don't have evidence for?",
    answer:
      "It's reported as a gap in the JD compatibility view, not quietly invented to close the score.",
  },
];

/** "Prepare better" section (redesign brief §5) — short, real reasons, not a testimonial.
 *  There is no fabricated review here on purpose: the product doesn't get to invent a
 *  user's experience, and this page doesn't get to invent one either. */
export const prepareBullets: string[] = [
  'Tailored to your real, verified experience',
  'Every generated line traces to an evidence ID',
  'Built to parse cleanly, not just look good',
];

export interface Stat {
  value: number;
  suffix?: string;
  label: string;
}

/** "By the numbers" (redesign brief §6) — four real, verifiable facts about the system
 *  itself, not usage/social-proof metrics the product has no real numbers for yet (see
 *  README "Status": a first vertical slice, not a launched product with a user base).
 *  Sources: AtsScoringEngine (7 checks), Profile (6 evidence sections),
 *  jd-analysis.schema.json (7 requirement categories), GenerationType (4 ways to apply). */
export const stats: Stat[] = [
  { value: 7, label: 'Deterministic ATS checks, computed in Java' },
  { value: 6, label: 'Evidence-bearing profile sections' },
  { value: 7, label: 'JD requirement categories, each weighted' },
  { value: 4, label: 'Ways to apply — resume, cover letter, email, or all three' },
];

export interface ResourceTeaser {
  category: string;
  title: string;
  description: string;
}

/** "Guides and tips" (redesign brief §8) — the product has no blog yet, so these are
 *  short, real answers rather than links to articles that don't exist; each "Read more"
 *  points at the FAQ section below, which carries the full answer. */
export const resourceTeasers: ResourceTeaser[] = [
  {
    category: 'Grounding',
    title: "Will it invent experience I don't have?",
    description: "No — every statement traces to an evidence ID, or it's removed and reported as a gap.",
  },
  {
    category: 'Scoring',
    title: 'How is the ATS score actually computed?',
    description: 'Seven weighted checks running in Java — deterministic, and explainable down to the sub-check.',
  },
  {
    category: 'Privacy',
    title: 'Where does my data live?',
    description: 'MongoDB Atlas and a private object store. Another user requesting your resume gets a 404.',
  },
  {
    category: 'Applications',
    title: 'Will anything be sent without me seeing it?',
    description: 'Never. Gmail integration only ever creates a draft — sending is always your own action.',
  },
];

export interface UnderstandStep {
  title: string;
  description: string;
}

/** "Everything starts with understanding your profile" (redesign brief §9). */
export const understandSteps: UnderstandStep[] = [
  { title: 'Understand your experience', description: 'Real roles, projects and skills, each with a stable evidence ID.' },
  { title: 'Match it with the role', description: "The JD's own requirements, extracted and classified — not guessed." },
  { title: 'Build a grounded application', description: 'Tailored content that only ever cites what you actually gave it.' },
];
