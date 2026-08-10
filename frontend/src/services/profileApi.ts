/**
 * profile-service — see docs/API_CATALOG.md &sect;3 (Milestone 3).
 * Personal information, education, experience, skills, projects, certifications and
 * achievements are all implemented — every section shares the same evidence-id pattern.
 */
import { apiFetch } from './apiClient';

export interface PersonalInformation {
  fullName: string | null;
  headline: string | null;
  email: string | null;
  phone: string | null;
  links: string[];
}

export interface Experience {
  evidenceId: string;
  company: string | null;
  title: string | null;
  employmentType: string | null;
  start: string | null;
  end: string | null;
  current: boolean;
  location: string | null;
  bullets: string[];
  technologies: string[];
  metrics: string[];
}

export interface Education {
  evidenceId: string;
  institution: string | null;
  degree: string | null;
  field: string | null;
  start: string | null;
  end: string | null;
  grade: string | null;
  description: string | null;
}

export interface Skill {
  evidenceId: string;
  name: string | null;
  category: string | null;
  proficiency: string | null;
  yearsOfExperience: number | null;
}

export interface Project {
  evidenceId: string;
  name: string | null;
  description: string | null;
  role: string | null;
  technologies: string[];
  metrics: string[];
  githubUrl: string | null;
  liveUrl: string | null;
  start: string | null;
  end: string | null;
}

export interface Certification {
  evidenceId: string;
  name: string | null;
  issuer: string | null;
  issuedOn: string | null;
  expiresOn: string | null;
  credentialId: string | null;
  credentialUrl: string | null;
}

export interface Achievement {
  evidenceId: string;
  title: string | null;
  description: string | null;
  date: string | null;
}

export interface ProfileResponse {
  personalInformation: PersonalInformation;
  education: Education[];
  experiences: Experience[];
  skills: Skill[];
  projects: Project[];
  certifications: Certification[];
  achievements: Achievement[];
}

export interface EvidenceItem {
  evidenceId: string;
  type: string;
  title: string | null;
  organisation: string | null;
  description: string | null;
  technologies: string[];
  metrics: string[];
  startDate: string | null;
  endDate: string | null;
}

export interface ExperienceInput {
  company: string;
  title: string;
  employmentType?: string | undefined;
  start?: string | undefined;
  end?: string | undefined;
  current: boolean;
  location?: string | undefined;
  bullets: string[];
  technologies: string[];
  metrics: string[];
}

export interface EducationInput {
  institution: string;
  degree: string;
  field?: string | undefined;
  start?: string | undefined;
  end?: string | undefined;
  grade?: string | undefined;
  description?: string | undefined;
}

export interface SkillInput {
  name: string;
  category?: string | undefined;
  proficiency?: string | undefined;
  yearsOfExperience?: number | undefined;
}

export interface ProjectInput {
  name: string;
  description?: string | undefined;
  role?: string | undefined;
  technologies: string[];
  metrics: string[];
  githubUrl?: string | undefined;
  liveUrl?: string | undefined;
  start?: string | undefined;
  end?: string | undefined;
}

export interface CertificationInput {
  name: string;
  issuer: string;
  issuedOn?: string | undefined;
  expiresOn?: string | undefined;
  credentialId?: string | undefined;
  credentialUrl?: string | undefined;
}

export interface AchievementInput {
  title: string;
  description?: string | undefined;
  date?: string | undefined;
}

export function getProfile(): Promise<ProfileResponse> {
  return apiFetch<ProfileResponse>('/api/profile');
}

export function updatePersonalInformation(input: {
  fullName: string;
  headline?: string | undefined;
  email?: string | undefined;
  phone?: string | undefined;
  links: string[];
}): Promise<ProfileResponse> {
  return apiFetch<ProfileResponse>('/api/profile', { method: 'PUT', body: JSON.stringify(input) });
}

function crud<TInput>(section: string) {
  return {
    add: (input: TInput): Promise<ProfileResponse> =>
      apiFetch<ProfileResponse>(`/api/profile/${section}`, { method: 'POST', body: JSON.stringify(input) }),
    update: (evidenceId: string, input: TInput): Promise<ProfileResponse> =>
      apiFetch<ProfileResponse>(`/api/profile/${section}/${evidenceId}`, {
        method: 'PUT',
        body: JSON.stringify(input),
      }),
    remove: (evidenceId: string): Promise<ProfileResponse> =>
      apiFetch<ProfileResponse>(`/api/profile/${section}/${evidenceId}`, { method: 'DELETE' }),
  };
}

const experienceCrud = crud<ExperienceInput>('experience');
export const addExperience = experienceCrud.add;
export const updateExperience = experienceCrud.update;
export const deleteExperience = experienceCrud.remove;

const educationCrud = crud<EducationInput>('education');
export const addEducation = educationCrud.add;
export const updateEducation = educationCrud.update;
export const deleteEducation = educationCrud.remove;

const skillCrud = crud<SkillInput>('skills');
export const addSkill = skillCrud.add;
export const updateSkill = skillCrud.update;
export const deleteSkill = skillCrud.remove;

const projectCrud = crud<ProjectInput>('projects');
export const addProject = projectCrud.add;
export const updateProject = projectCrud.update;
export const deleteProject = projectCrud.remove;

const certificationCrud = crud<CertificationInput>('certifications');
export const addCertification = certificationCrud.add;
export const updateCertification = certificationCrud.update;
export const deleteCertification = certificationCrud.remove;

const achievementCrud = crud<AchievementInput>('achievements');
export const addAchievement = achievementCrud.add;
export const updateAchievement = achievementCrud.update;
export const deleteAchievement = achievementCrud.remove;

export function getEvidence(): Promise<EvidenceItem[]> {
  return apiFetch<EvidenceItem[]>('/api/profile/evidence');
}

/**
 * There's no backend "onboarding complete" flag — this is a client-side heuristic over
 * real data: a name and at least one experience is enough to generate against. Unchanged
 * from before, so existing users with just personal+experience keep working exactly as
 * they did (backward compatible) — the other sections are optional enrichment, not gates.
 */
export function isProfileComplete(profile: ProfileResponse): boolean {
  return Boolean(profile.personalInformation.fullName?.trim()) && profile.experiences.length > 0;
}

export interface ProfileCompletion {
  percentage: number;
  sections: {
    personal: boolean;
    education: boolean;
    experience: boolean;
    skills: boolean;
    projects: boolean;
    certifications: boolean;
    achievements: boolean;
  };
}

/**
 * A section counts as complete only if it holds real persisted data — never just because
 * the user visited that step. Every section is weighted equally.
 */
export function computeProfileCompletion(profile: ProfileResponse): ProfileCompletion {
  const sections = {
    personal: Boolean(profile.personalInformation.fullName?.trim()),
    education: profile.education.length > 0,
    experience: profile.experiences.length > 0,
    skills: profile.skills.length > 0,
    projects: profile.projects.length > 0,
    certifications: profile.certifications.length > 0,
    achievements: profile.achievements.length > 0,
  };
  const total = Object.keys(sections).length;
  const done = Object.values(sections).filter(Boolean).length;
  return { percentage: Math.round((done / total) * 100), sections };
}
