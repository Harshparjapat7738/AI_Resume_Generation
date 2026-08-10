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

export const workflowSteps: WorkflowStep[] = [
  {
    index: '01',
    title: 'Build a verified profile',
    description:
      'Personal info, education, experience, skills, certifications and projects — each fact carries a stable evidence ID the rest of the pipeline can point back to.',
  },
  {
    index: '02',
    title: 'Add a job description',
    description:
      'Paste text, upload a PDF or DOCX, or supply a URL fetched through an SSRF-hardened client. You confirm the extracted JD before anything is generated.',
  },
  {
    index: '03',
    title: 'Generate, grounded',
    description:
      'A two-stage pipeline selects evidence, then writes content — validated against a JSON schema and a grounding check before it ever reaches you.',
  },
  {
    index: '04',
    title: 'See your ATS score',
    description:
      'Ten weighted checks, computed in Java and never asked of the LLM, explainable all the way down to the sub-check.',
  },
  {
    index: '05',
    title: 'Apply with confidence',
    description:
      'A tailored resume, cover letter and email — plus a Gmail draft you review yourself. Nothing is ever sent on your behalf.',
  },
];

export interface AtsCheck {
  label: string;
  score: number;
}

export const atsChecks: AtsCheck[] = [
  { label: 'Requirement coverage', score: 92 },
  { label: 'Keyword match', score: 87 },
  { label: 'Seniority alignment', score: 95 },
  { label: 'Section structure', score: 100 },
  { label: 'Recency', score: 78 },
  { label: 'Contact & parse safety', score: 100 },
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
