import { useRef, useState } from 'react';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { showToast } from '@/components/ui/toast';
import * as profileApi from '@/services/profileApi';
import type { ProfileResponse } from '@/services/profileApi';

/**
 * Shared step-nav for every repeatable-data section (education, experience, skills,
 * projects, certifications, achievements) — used by both the onboarding wizard
 * (OnboardingPage.tsx) and the profile dashboard's single-section view (ProfilePage.tsx).
 * Every entry in these sections is already persisted the instant its own card form is
 * submitted (each Manager's add.../update... calls) — so there's nothing left to "save"
 * here. "Save & continue" instead re-fetches the profile from profile-service and confirms
 * the entries are actually there before advancing, rather than trusting local/optimistic
 * state. A confirmation that fails (network blip, session hiccup) leaves the user on this
 * section with their data untouched and a visible reason, instead of silently carrying them
 * onward on an unconfirmed save. When the section is empty, this is just "Skip"/"Continue" —
 * a synchronous, unconfirmed advance.
 */
export function ConfirmedStepNav({
  onBack,
  onNext,
  onConfirmSaved,
  hasData,
  verify,
  itemNamePlural,
  showBack = true,
  nextLabel,
}: {
  onBack: () => void;
  onNext: () => void;
  onConfirmSaved: (profile: ProfileResponse) => void;
  hasData: boolean;
  /** Checks the freshly-refetched profile actually holds this section's data. */
  verify: (profile: ProfileResponse) => boolean;
  /** Used only in the (should-never-happen) failed-confirmation message, e.g. "experience entries". */
  itemNamePlural: string;
  /** Hidden on the first section (onboarding's Personal step, the profile dashboard's first
   *  nav item) — there's nothing before it to go back to. */
  showBack?: boolean;
  /** Defaults to "Save & continue" / "Skip" (the onboarding wording) — the profile dashboard
   *  overrides this to "Save changes" / "Continue" since there's no wizard to skip through. */
  nextLabel?: { withData: string; empty: string };
}) {
  const [status, setStatus] = useState<'idle' | 'pending' | 'saved'>('idle');
  const [error, setError] = useState<unknown>(null);
  // Guards a fast double-click landing two confirmations before the first re-render disables
  // the button — same pattern as PersonalInfoForm's submittingRef.
  const submittingRef = useRef(false);

  const handleNext = async () => {
    if (!hasData) {
      onNext();
      return;
    }
    if (submittingRef.current) return;
    submittingRef.current = true;
    setError(null);
    setStatus('pending');
    try {
      const fresh = await profileApi.getProfile();
      onConfirmSaved(fresh);
      if (!verify(fresh)) {
        // Shouldn't happen (hasData was true a moment ago) — but if the server disagrees with
        // what we expected to find, that's a failed confirmation, not a pass.
        throw new Error(`Your ${itemNamePlural} could not be confirmed as saved. Please try again.`);
      }
      setStatus('saved');
      showToast('✓ Saved — your data is stored.');
      await new Promise((resolve) => setTimeout(resolve, 500)); // let "✓ Saved" register
      onNext();
      return; // this section is likely about to unmount; no need to reset local state
    } catch (err) {
      setStatus('idle');
      setError(err);
    } finally {
      submittingRef.current = false;
    }
  };

  const busy = status !== 'idle';
  const withDataLabel = nextLabel?.withData ?? 'Save & continue';
  const emptyLabel = nextLabel?.empty ?? 'Skip';
  const label = status === 'pending' ? 'Saving…' : status === 'saved' ? '✓ Saved' : hasData ? withDataLabel : emptyLabel;

  return (
    <div>
      {error !== null && (
        <div className="mt-6">
          <ErrorBanner error={error} />
        </div>
      )}
      <div className={`mt-6 flex items-center gap-3 ${showBack ? 'justify-between' : 'justify-end'}`}>
        {showBack && (
          <Button variant="ghost" onClick={onBack} disabled={busy}>
            Back
          </Button>
        )}
        <Button variant={hasData ? 'primary' : 'ghost'} onClick={handleNext} loading={status === 'pending'} disabled={busy}>
          {label}
        </Button>
      </div>
    </div>
  );
}
