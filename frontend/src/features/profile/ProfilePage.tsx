import { useState } from 'react';
import type { ReactNode } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { AppHeader } from '@/components/layout/AppHeader';
import { Card } from '@/components/ui/Card';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { FullScreenSpinner } from '@/components/ui/FullScreenSpinner';
import { AchievementsManager } from '@/features/profile-shared/AchievementsManager';
import { CertificationsManager } from '@/features/profile-shared/CertificationsManager';
import { ConfirmedStepNav } from '@/features/profile-shared/ConfirmedStepNav';
import { EducationManager } from '@/features/profile-shared/EducationManager';
import { ExperienceManager } from '@/features/profile-shared/ExperienceManager';
import { PersonalInfoForm } from '@/features/profile-shared/PersonalInfoForm';
import { ProjectsManager } from '@/features/profile-shared/ProjectsManager';
import { SkillsManager } from '@/features/profile-shared/SkillsManager';
import * as profileApi from '@/services/profileApi';
import { computeProfileCompletion } from '@/services/profileApi';
import type { ProfileResponse } from '@/services/profileApi';
import { ProfileHeaderCard } from './components/ProfileHeaderCard';
import { ProfileSidebar } from './components/ProfileSidebar';
import { SectionShell } from './components/SectionShell';
import { PROFILE_SECTIONS } from './sections';

export function ProfilePage() {
  const queryClient = useQueryClient();
  const profileQuery = useQuery({ queryKey: ['profile'], queryFn: profileApi.getProfile });

  if (profileQuery.isLoading) {
    return <FullScreenSpinner label="Loading your profile…" />;
  }
  if (profileQuery.isError || !profileQuery.data) {
    return (
      <div className="min-h-screen bg-void">
        <AppHeader />
        <main className="mx-auto max-w-2xl px-6 py-12">
          <ErrorBanner error={profileQuery.error} />
        </main>
      </div>
    );
  }

  const updateCache = (updated: ProfileResponse) => queryClient.setQueryData(['profile'], updated);

  return <ProfileDashboard profile={profileQuery.data} onChanged={updateCache} />;
}

/** Split out from ProfilePage so this hook only ever runs once profile data exists. */
function ProfileDashboard({
  profile,
  onChanged,
}: {
  profile: ProfileResponse;
  onChanged: (profile: ProfileResponse) => void;
}) {
  const completion = computeProfileCompletion(profile);
  // Only ever one section mounted at a time — see ProfileSidebar/SectionPanel below. Not
  // persisted across reloads (unlike onboarding's step): this is a dashboard the user returns
  // to repeatedly, and always starting back on Personal is more predictable than resuming
  // wherever a previous visit, possibly days ago, happened to leave off.
  const [activeIndex, setActiveIndex] = useState(0);

  const goNext = () => setActiveIndex((i) => Math.min(i + 1, PROFILE_SECTIONS.length - 1));
  const goBack = () => setActiveIndex((i) => Math.max(i - 1, 0));
  const activeKey = PROFILE_SECTIONS[activeIndex]?.key;

  return (
    <div className="min-h-screen bg-void">
      <AppHeader />
      <ProfileHeaderCard profile={profile} completion={completion} onEditProfile={() => setActiveIndex(0)} />

      <main className="mx-auto max-w-6xl px-4 py-8 sm:px-6 lg:py-10">
        <div className="grid gap-8 lg:grid-cols-[220px_1fr] lg:gap-10">
          <ProfileSidebar sections={completion.sections} activeIndex={activeIndex} onSelect={setActiveIndex} />

          <div key={activeKey} className="min-w-0 animate-step-in">
            {activeKey === 'personal' && (
              <SectionPanel title="Personal Information" description="Basic information used across your applications.">
                <PersonalInfoForm profile={profile} onSaved={onChanged} afterSave={goNext} submitLabel="Save & continue" />
              </SectionPanel>
            )}

            {activeKey === 'education' && (
              <SectionPanel title="Education" description="Add your educational background.">
                <EducationManager profile={profile} onChanged={onChanged} />
                <ConfirmedStepNav
                  onBack={goBack}
                  onNext={goNext}
                  onConfirmSaved={onChanged}
                  hasData={completion.sections.education}
                  verify={(p) => p.education.length > 0}
                  itemNamePlural="education entries"
                />
              </SectionPanel>
            )}

            {activeKey === 'experience' && (
              <SectionPanel
                title="Experience"
                description="Every fact you add here gets a stable evidence ID — generated content can only cite what's listed below."
              >
                <ExperienceManager profile={profile} onChanged={onChanged} />
                <ConfirmedStepNav
                  onBack={goBack}
                  onNext={goNext}
                  onConfirmSaved={onChanged}
                  hasData={completion.sections.experience}
                  verify={(p) => p.experiences.length > 0}
                  itemNamePlural="experience entries"
                />
              </SectionPanel>
            )}

            {activeKey === 'skills' && (
              <SectionPanel title="Skills" description="Languages, frameworks, tools — anything relevant to the roles you're targeting.">
                <SkillsManager profile={profile} onChanged={onChanged} />
                <ConfirmedStepNav
                  onBack={goBack}
                  onNext={goNext}
                  onConfirmSaved={onChanged}
                  hasData={completion.sections.skills}
                  verify={(p) => p.skills.length > 0}
                  itemNamePlural="skills"
                />
              </SectionPanel>
            )}

            {activeKey === 'projects' && (
              <SectionPanel title="Projects" description="Personal, open-source, or work projects worth citing.">
                <ProjectsManager profile={profile} onChanged={onChanged} />
                <ConfirmedStepNav
                  onBack={goBack}
                  onNext={goNext}
                  onConfirmSaved={onChanged}
                  hasData={completion.sections.projects}
                  verify={(p) => p.projects.length > 0}
                  itemNamePlural="projects"
                />
              </SectionPanel>
            )}

            {activeKey === 'certifications' && (
              <SectionPanel title="Certifications" description="Certifications back up claims a resume alone can't prove.">
                <CertificationsManager profile={profile} onChanged={onChanged} />
                <ConfirmedStepNav
                  onBack={goBack}
                  onNext={goNext}
                  onConfirmSaved={onChanged}
                  hasData={completion.sections.certifications}
                  verify={(p) => p.certifications.length > 0}
                  itemNamePlural="certifications"
                />
              </SectionPanel>
            )}

            {activeKey === 'achievements' && (
              <SectionPanel title="Achievements" description="Awards, competitions, publications, leadership — anything worth citing.">
                <AchievementsManager profile={profile} onChanged={onChanged} />
                <ConfirmedStepNav
                  onBack={goBack}
                  onNext={goNext}
                  onConfirmSaved={onChanged}
                  hasData={completion.sections.achievements}
                  verify={(p) => p.achievements.length > 0}
                  itemNamePlural="achievements"
                  nextLabel={{ withData: 'Save changes', empty: 'Done' }}
                />
              </SectionPanel>
            )}
          </div>
        </div>
      </main>
    </div>
  );
}

/** The one large content card the active section renders into — title, short description,
 *  then whatever that section hands it (existing entries, an add/edit form when open, and
 *  its own Back/Save & continue nav). */
function SectionPanel({
  title,
  description,
  children,
}: {
  title: string;
  description: string;
  children: ReactNode;
}) {
  return (
    <Card className="!p-6 sm:!p-8">
      <SectionShell id={`section-${title.toLowerCase().replace(/\s+/g, '-')}`} title={title} description={description}>
        {children}
      </SectionShell>
    </Card>
  );
}
