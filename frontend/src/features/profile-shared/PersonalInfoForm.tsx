import { zodResolver } from '@hookform/resolvers/zod';
import { useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { TextField } from '@/components/ui/TextField';
import * as profileApi from '@/services/profileApi';
import type { ProfileResponse } from '@/services/profileApi';

const schema = z.object({
  fullName: z.string().trim().min(1, 'Enter your full name').max(200),
  headline: z.string().trim().max(200).optional(),
  email: z.string().trim().email('Enter a valid email address').optional().or(z.literal('')),
  phone: z.string().trim().max(40).optional(),
});

type FormValues = z.infer<typeof schema>;

const wait = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

function defaultsFrom(profile: ProfileResponse): FormValues {
  return {
    fullName: profile.personalInformation.fullName ?? '',
    headline: profile.personalInformation.headline ?? '',
    email: profile.personalInformation.email ?? '',
    phone: profile.personalInformation.phone ?? '',
  };
}

/** One read-only label/value pair for the Profile page's view mode. */
function InfoField({ label, value }: { label: string; value: string | null | undefined }) {
  return (
    <div>
      <dt className="text-xs text-ink-faint">{label}</dt>
      <dd className={`mt-1 text-sm ${value ? 'text-ink' : 'italic text-ink-faint'}`}>{value || 'Not added yet'}</dd>
    </div>
  );
}

export function PersonalInfoForm({
  profile,
  onSaved,
  afterSave,
  submitLabel = 'Save',
  savingLabel = 'Saving…',
  dashboard = false,
}: {
  profile: ProfileResponse;
  onSaved: (profile: ProfileResponse) => void;
  /**
   * Called once the save has succeeded AND the "✓ Saved" state has been visible for a
   * beat — never before. Onboarding uses this to advance to the next step; the Profile
   * page's dashboard usage omits it and instead settles back to its own view mode in place.
   * This is the only thing that may trigger navigation — there is no path to it from a
   * failed submission or from validation errors.
   */
  afterSave?: () => void;
  submitLabel?: string;
  savingLabel?: string;
  /** Onboarding's single-column wizard step (always the form) vs. the Profile page's
   *  view-mode-first card (labels + an Edit action, matching every other section there). */
  dashboard?: boolean;
}) {
  const hasData = Boolean(profile.personalInformation.fullName?.trim());
  const [mode, setMode] = useState<'view' | 'edit'>(dashboard && hasData ? 'view' : 'edit');
  const [submitError, setSubmitError] = useState<unknown>(null);
  const [status, setStatus] = useState<'idle' | 'saving' | 'saved'>('idle');
  // Guards against a fast double-submit landing two requests before React re-renders the
  // disabled button — isSubmitting/status only flip after the first render commits.
  const submittingRef = useRef(false);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: defaultsFrom(profile),
  });

  const startEdit = () => {
    reset(defaultsFrom(profile));
    setSubmitError(null);
    setMode('edit');
  };

  const cancelEdit = () => {
    setSubmitError(null);
    setMode('view');
  };

  const onSubmit = async (values: FormValues) => {
    if (submittingRef.current) return;
    submittingRef.current = true;
    setSubmitError(null);
    setStatus('saving');
    try {
      const updated = await profileApi.updatePersonalInformation({
        fullName: values.fullName,
        headline: values.headline,
        email: values.email,
        phone: values.phone,
        links: profile.personalInformation.links,
      });
      // Cache/completion update happens immediately — never gated behind the animation.
      onSaved(updated);
      setStatus('saved');
      if (afterSave) {
        await wait(600); // let "✓ Saved" register before the step changes
        afterSave();
        return; // this step is about to unmount; no need to reset local state
      }
      await wait(dashboard ? 900 : 1800);
      setStatus('idle');
      if (dashboard) setMode('view');
    } catch (error) {
      setStatus('idle');
      setSubmitError(error);
    } finally {
      submittingRef.current = false;
    }
  };

  const busy = isSubmitting || status !== 'idle';
  const buttonLabel = status === 'saving' ? savingLabel : status === 'saved' ? '✓ Saved' : dashboard ? 'Save changes' : submitLabel;

  if (dashboard && mode === 'view') {
    const info = profile.personalInformation;
    return (
      <Card>
        <div className="flex items-center justify-between gap-3">
          <p className="text-xs font-semibold uppercase tracking-wide text-ink-faint">Contact details</p>
          <button
            type="button"
            onClick={startEdit}
            className="text-sm font-medium text-ink-muted transition-colors hover:text-ink"
          >
            Edit
          </button>
        </div>
        <dl className="mt-4 grid gap-x-6 gap-y-4 sm:grid-cols-2">
          <InfoField label="Full name" value={info.fullName} />
          <InfoField label="Professional headline" value={info.headline} />
          <InfoField label="Email" value={info.email} />
          <InfoField label="Phone" value={info.phone} />
        </dl>
      </Card>
    );
  }

  if (dashboard) {
    return (
      <Card>
        <form className="space-y-4" onSubmit={handleSubmit(onSubmit)} noValidate>
          {submitError !== null ? <ErrorBanner error={submitError} /> : null}
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField label="Full name" error={errors.fullName?.message} {...register('fullName')} />
            <TextField
              label="Professional headline"
              placeholder="Backend Engineer"
              error={errors.headline?.message}
              {...register('headline')}
            />
            <TextField label="Email" type="email" error={errors.email?.message} {...register('email')} />
            <TextField label="Phone" error={errors.phone?.message} {...register('phone')} />
          </div>
          <div className="flex items-center gap-3">
            <Button type="submit" loading={status === 'saving'} disabled={busy}>
              {buttonLabel}
            </Button>
            {hasData && (
              <Button type="button" variant="ghost" onClick={cancelEdit} disabled={busy}>
                Cancel
              </Button>
            )}
          </div>
        </form>
      </Card>
    );
  }

  return (
    <form className="space-y-4" onSubmit={handleSubmit(onSubmit)} noValidate>
      {submitError !== null ? <ErrorBanner error={submitError} /> : null}
      <TextField label="Full name" error={errors.fullName?.message} {...register('fullName')} />
      <TextField
        label="Professional headline"
        placeholder="Backend Engineer"
        error={errors.headline?.message}
        {...register('headline')}
      />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="Email" type="email" error={errors.email?.message} {...register('email')} />
        <TextField label="Phone" error={errors.phone?.message} {...register('phone')} />
      </div>
      <Button type="submit" loading={status === 'saving'} disabled={busy} className="w-full sm:w-auto">
        {buttonLabel}
      </Button>
    </form>
  );
}
