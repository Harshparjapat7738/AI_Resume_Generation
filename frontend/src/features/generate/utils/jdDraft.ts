/**
 * Client-side draft for the Job Description step — nothing is sent to the backend until
 * "Continue"/"Fetch job description" succeeds, so anything typed before that only ever lived in
 * component state, which React discards the moment the page unmounts (e.g. a stray browser back,
 * or an accidental refresh). This persists the in-progress paste text / URL / active tab to
 * sessionStorage so it survives all of that — cleared automatically the moment a job description
 * is actually submitted, since from then on the real thing is safely stored server-side
 * (jd-service) and addressable by its own id, which the skill-gap step reads instead.
 *
 * sessionStorage (not localStorage): scoped to this one tab for the life of this one session —
 * a draft shouldn't reappear in a different tab, and shouldn't outlive the browser being closed.
 */
const STORAGE_KEY = 'careerforge:jd-draft';

export interface JdDraft {
  tab: 'paste' | 'url';
  jobDescriptionText: string;
  url: string;
}

const EMPTY_DRAFT: JdDraft = { tab: 'paste', jobDescriptionText: '', url: '' };

/** Never throws — private browsing / storage disabled / a malformed leftover value from an
 *  older build all just mean "no draft", not a crash. */
export function readJdDraft(): JdDraft {
  if (typeof window === 'undefined') return EMPTY_DRAFT;
  try {
    const raw = window.sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return EMPTY_DRAFT;
    const parsed = JSON.parse(raw) as Partial<JdDraft>;
    return {
      tab: parsed.tab === 'url' ? 'url' : 'paste',
      jobDescriptionText: typeof parsed.jobDescriptionText === 'string' ? parsed.jobDescriptionText : '',
      url: typeof parsed.url === 'string' ? parsed.url : '',
    };
  } catch {
    return EMPTY_DRAFT;
  }
}

export function writeJdDraft(patch: Partial<JdDraft>): void {
  if (typeof window === 'undefined') return;
  try {
    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ ...readJdDraft(), ...patch }));
  } catch {
    // Storage full/disabled — the draft just won't survive navigation; not worth surfacing.
  }
}

/** Called once a job description is actually submitted (paste or URL) — from then on the
 *  server-side JD is the source of truth, so holding onto the local draft any longer would only
 *  risk resurrecting stale text into an unrelated future visit to this step. */
export function clearJdDraft(): void {
  if (typeof window === 'undefined') return;
  try {
    window.sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // ignore
  }
}
