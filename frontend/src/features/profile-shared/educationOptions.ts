/**
 * Curated degree/field-of-study values for the Education form's dropdowns. `degree` and
 * `field` are still plain strings on the wire (profile-service's contract is unchanged —
 * see docs/API_CATALOG.md) — this is purely a frontend picklist over that same string, with
 * "Other" always available as an escape hatch that reveals a free-text field.
 */

export const OTHER = 'Other' as const;

export const DEGREE_OPTIONS = [
  'High School / Secondary',
  'Diploma',
  "Associate's Degree",
  "Bachelor's — B.Tech / B.E.",
  "Bachelor's — B.Sc.",
  "Bachelor's — B.A.",
  "Bachelor's — B.Com.",
  "Bachelor's — BBA",
  "Master's — M.Tech / M.E.",
  "Master's — M.Sc.",
  "Master's — M.A.",
  "Master's — MBA",
  "Master's — M.Com.",
  'Doctorate — Ph.D.',
  OTHER,
] as const;

const FIELD_OPTIONS_BY_DEGREE: Record<string, string[]> = {
  'High School / Secondary': ['Science', 'Commerce', 'Arts / Humanities'],
  Diploma: [
    'Computer Science',
    'Information Technology',
    'Mechanical Engineering',
    'Electrical Engineering',
    'Civil Engineering',
    'Electronics',
  ],
  "Associate's Degree": ['Computer Science', 'Business Administration', 'Liberal Arts', 'Engineering'],
  "Bachelor's — B.Tech / B.E.": [
    'Computer Science',
    'Information Technology',
    'Electronics & Communication',
    'Electrical Engineering',
    'Mechanical Engineering',
    'Civil Engineering',
    'Chemical Engineering',
    'AI & Machine Learning',
    'Data Science',
  ],
  "Bachelor's — B.Sc.": ['Computer Science', 'Physics', 'Chemistry', 'Mathematics', 'Biology', 'Statistics', 'Environmental Science'],
  "Bachelor's — B.A.": ['Economics', 'English', 'Psychology', 'Political Science', 'Sociology', 'History', 'Journalism & Mass Communication'],
  "Bachelor's — B.Com.": ['Accounting & Finance', 'Business Administration', 'Banking & Insurance', 'Economics'],
  "Bachelor's — BBA": ['Business Administration', 'Marketing', 'Finance', 'Human Resources', 'Operations'],
  "Master's — M.Tech / M.E.": [
    'Computer Science',
    'Information Technology',
    'AI & Machine Learning',
    'Data Science',
    'Electronics & Communication',
    'VLSI Design',
    'Structural Engineering',
  ],
  "Master's — M.Sc.": ['Computer Science', 'Physics', 'Chemistry', 'Mathematics', 'Data Science', 'Biotechnology'],
  "Master's — M.A.": ['Economics', 'English', 'Psychology', 'Political Science', 'Sociology'],
  "Master's — MBA": ['Finance', 'Marketing', 'Human Resources', 'Operations', 'Business Analytics', 'International Business', 'Entrepreneurship'],
  "Master's — M.Com.": ['Accounting & Finance', 'Business Administration', 'Economics'],
  'Doctorate — Ph.D.': ['Computer Science', 'Engineering', 'Physical Sciences', 'Life Sciences', 'Social Sciences', 'Management'],
};

/** Field-of-study options for a given degree, always ending in "Other". Unknown/blank
 *  degree returns just ["Other"] so the field select is never left empty. */
export function fieldOptionsFor(degree: string | undefined): string[] {
  const base = (degree && FIELD_OPTIONS_BY_DEGREE[degree]) || [];
  return [...base, OTHER];
}
