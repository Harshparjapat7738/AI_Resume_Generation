import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { MonthField } from '@/components/ui/MonthField';
import { TextArea } from '@/components/ui/TextArea';
import { TextField } from '@/components/ui/TextField';
import * as profileApi from '@/services/profileApi';
import type { ProfileResponse } from '@/services/profileApi';
import { EmptyState } from '@/components/ui/EmptyState';
import { BriefcaseIcon } from '@/features/profile/components/icons';
import { RecordCard } from '@/features/profile/components/RecordCard';
import { SectionEditorToggle } from '@/features/profile/components/SectionEditorToggle';

const schema = z.object({
  company: z.string().trim().min(1, 'Enter the company name').max(200),
  title: z.string().trim().min(1, 'Enter your job title').max(200),
  start: z.string().trim().min(1, 'Select a start date').max(20),
  end: z.string().trim().max(20).optional(),
  current: z.boolean(),
  bullets: z.string().trim().min(1, 'Describe at least one responsibility or achievement'),
  technologies: z.string().trim().optional(),
});

type FormValues = z.infer<typeof schema>;

const splitLines = (value: string) =>
  value.split('\n').map((line) => line.trim()).filter(Boolean);

const splitCommas = (value: string | undefined) =>
  (value ?? '').split(',').map((v) => v.trim()).filter(Boolean);

/**
 * This is the "Evidence" architecture from earlier in the product — every entry gets a
 * stable evidenceId and is what the grounded-generation pipeline is allowed to cite. It's
 * real and load-bearing; what changed is where the user encounters it: as a profile/onboarding
 * section rather than a forced landing page after login.
 *
 * One card per entry, collapsed behind "+ Add Experience" when there's nothing (or nothing
 * being edited) to show — same pattern as EducationManager, used identically by the Profile
 * page and the onboarding wizard.
 */
export function ExperienceManager({
  profile,
  onChanged,
}: {
  profile: ProfileResponse;
  onChanged: (profile: ProfileResponse) => void;
}) {
  const [formError, setFormError] = useState<unknown>(null);
  const [deleteError, setDeleteError] = useState<unknown>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [isAdding, setIsAdding] = useState(false);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: { current: false } });

  const startValue = watch('start');
  const endValue = watch('end');
  const isCurrent = watch('current');

  const startAdd = () => {
    reset({ company: '', title: '', start: '', end: '', current: false, bullets: '', technologies: '' });
    setEditingId(null);
    setDeleteError(null);
    setIsAdding(true);
  };

  const startEdit = (evidenceId: string) => {
    const item = profile.experiences.find((e) => e.evidenceId === evidenceId);
    if (!item) return;
    setEditingId(evidenceId);
    setDeleteError(null);
    setIsAdding(true);
    reset({
      company: item.company ?? '',
      title: item.title ?? '',
      start: item.start ?? '',
      end: item.end ?? '',
      current: item.current,
      bullets: item.bullets.join('\n'),
      technologies: item.technologies.join(', '),
    });
  };

  const cancelEdit = () => {
    setEditingId(null);
    setIsAdding(false);
    setFormError(null);
    reset({ company: '', title: '', start: '', end: '', current: false, bullets: '', technologies: '' });
  };

  const onSubmit = async (values: FormValues) => {
    setFormError(null);
    try {
      const input = {
        company: values.company,
        title: values.title,
        start: values.start,
        end: values.current ? undefined : values.end,
        current: values.current,
        bullets: splitLines(values.bullets),
        technologies: splitCommas(values.technologies),
        metrics: [],
      };
      const updated = editingId
        ? await profileApi.updateExperience(editingId, input)
        : await profileApi.addExperience(input);
      onChanged(updated);
      cancelEdit();
    } catch (error) {
      setFormError(error);
    }
  };

  const handleDelete = async (evidenceId: string) => {
    setDeletingId(evidenceId);
    setDeleteError(null);
    try {
      const updated = await profileApi.deleteExperience(evidenceId);
      onChanged(updated);
      if (editingId === evidenceId) cancelEdit();
    } catch (error) {
      setDeleteError(error);
    } finally {
      setDeletingId(null);
    }
  };

  const currentRegister = register('current');
  const form = (
    <>
      <h3 className="text-sm font-semibold text-ink">{editingId ? 'Edit experience' : 'Add experience'}</h3>
      <form className="mt-4 space-y-4" onSubmit={handleSubmit(onSubmit)} noValidate>
        {formError !== null ? <ErrorBanner error={formError} /> : null}
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Company" error={errors.company?.message} {...register('company')} />
          <TextField label="Job title" error={errors.title?.message} {...register('title')} />
          <MonthField
            label="Start"
            value={startValue}
            onChange={(v) => setValue('start', v, { shouldValidate: true })}
            error={errors.start?.message}
            max={endValue || undefined}
          />
          <MonthField
            label="End"
            value={endValue}
            onChange={(v) => setValue('end', v, { shouldValidate: true })}
            error={errors.end?.message}
            min={startValue || undefined}
            disabled={isCurrent}
            placeholder={isCurrent ? 'Present' : 'Select month'}
          />
        </div>
        <label className="flex items-center gap-2 text-sm text-ink-muted">
          <input
            type="checkbox"
            className="h-4 w-4 rounded border-border"
            {...currentRegister}
            onChange={(e) => {
              currentRegister.onChange(e);
              if (e.target.checked) setValue('end', '');
            }}
          />
          I currently work here
        </label>
        <TextArea
          label="What did you do? One line each."
          hint="Be specific — every generated bullet must trace back to something written here."
          rows={4}
          error={errors.bullets?.message}
          {...register('bullets')}
        />
        <TextField
          label="Technologies (comma-separated)"
          placeholder="Java, Spring Boot, PostgreSQL"
          {...register('technologies')}
        />
        <div className="flex gap-3">
          <Button type="submit" variant="accent" loading={isSubmitting} disabled={isSubmitting}>
            {editingId ? 'Save changes' : 'Add experience'}
          </Button>
          <Button type="button" variant="ghost" onClick={cancelEdit} disabled={isSubmitting}>
            Cancel
          </Button>
        </div>
      </form>
    </>
  );

  const isOpen = isAdding || editingId !== null;
  const isEmpty = profile.experiences.length === 0;

  if (isEmpty && !isOpen) {
    return (
      <div className="space-y-4">
        <EmptyState
          icon={<BriefcaseIcon className="h-5 w-5" />}
          title="No experience added yet."
          hint="Add at least one role — every generated bullet traces back to something written here."
          action={
            <Button type="button" variant="secondary" onClick={startAdd}>
              + Add Experience
            </Button>
          }
        />
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {deleteError !== null ? <ErrorBanner error={deleteError} /> : null}
      {!isEmpty && (
        <div className="space-y-3">
          {profile.experiences.map((exp) => (
            <div
              key={exp.evidenceId}
              className={`animate-card-in transition-all duration-150 ${
                deletingId === exp.evidenceId ? 'scale-[0.98] opacity-50' : 'scale-100 opacity-100'
              }`}
            >
              <RecordCard
                title={
                  <span className="inline-flex items-center gap-2">
                    <span className="h-2 w-2 shrink-0 rounded-full bg-ember-soft" aria-hidden="true" />
                    {exp.title} — {exp.company}
                  </span>
                }
                meta={`${exp.start} – ${exp.current ? 'Present' : exp.end}`}
                tags={exp.technologies}
                onEdit={() => startEdit(exp.evidenceId)}
                onDelete={() => handleDelete(exp.evidenceId)}
                deleting={deletingId === exp.evidenceId}
                removeLabel="Remove experience entry"
              >
                {exp.bullets.length > 0 && (
                  <ul className="mt-2 list-inside list-disc space-y-1 text-sm text-ink-muted">
                    {exp.bullets.map((bullet, i) => (
                      <li key={i}>{bullet}</li>
                    ))}
                  </ul>
                )}
              </RecordCard>
            </div>
          ))}
        </div>
      )}
      <SectionEditorToggle open={isOpen} addLabel="Add Experience" onOpen={startAdd}>
        {form}
      </SectionEditorToggle>
    </div>
  );
}
