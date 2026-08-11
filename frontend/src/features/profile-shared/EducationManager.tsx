import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { MonthField } from '@/components/ui/MonthField';
import { Select } from '@/components/ui/Select';
import { TextField } from '@/components/ui/TextField';
import * as profileApi from '@/services/profileApi';
import type { ProfileResponse } from '@/services/profileApi';
import { EmptyState } from '@/components/ui/EmptyState';
import { GraduationCapIcon } from '@/features/profile/components/icons';
import { RecordCard } from '@/features/profile/components/RecordCard';
import { SectionEditorToggle } from '@/features/profile/components/SectionEditorToggle';
import { DEGREE_OPTIONS, OTHER, fieldOptionsFor } from './educationOptions';

const schema = z
  .object({
    institution: z.string().trim().min(1, 'Enter the institution name').max(200),
    degree: z.string().trim().min(1, 'Select your degree').max(200),
    degreeOther: z.string().trim().max(200).optional(),
    field: z.string().trim().max(200).optional(),
    fieldOther: z.string().trim().max(200).optional(),
    start: z.string().trim().max(20).optional(),
    end: z.string().trim().max(20).optional(),
    grade: z.string().trim().max(40).optional(),
  })
  .superRefine((values, ctx) => {
    if (values.degree === OTHER && !values.degreeOther?.trim()) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['degreeOther'], message: 'Specify your degree' });
    }
  });

type FormValues = z.infer<typeof schema>;

const emptyValues: FormValues = {
  institution: '',
  degree: '',
  degreeOther: '',
  field: '',
  fieldOther: '',
  start: '',
  end: '',
  grade: '',
};

/** Maps a persisted (freeform, possibly legacy) string onto one of the picklist options —
 *  falling back to "Other" + the original text so nothing already saved is ever silently
 *  dropped just because it predates this dropdown or isn't in the curated list. */
function toPicklistValue(options: readonly string[], stored: string | null): { select: string; other: string } {
  if (!stored) return { select: '', other: '' };
  return options.includes(stored) ? { select: stored, other: '' } : { select: OTHER, other: stored };
}

/**
 * Education section — one card per entry, added/removed independently, collapsed behind
 * "+ Add Education" when there's nothing (or nothing being edited) to show. Used identically
 * by the Profile page and the onboarding wizard; there's no reason those two contexts should
 * behave differently, and having them diverge was the bug (onboarding used to render this
 * section's add-form permanently open with no cards/remove affordance — see EducationManager
 * history). Every entry is still saved to profile-service the instant its own form is
 * submitted (`addEducation`/`updateEducation` below) — "Save & continue" in the wizard
 * (OnboardingPage.tsx) re-confirms that against the server before advancing, it doesn't do
 * the saving itself.
 */
export function EducationManager({
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
    getValues,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: emptyValues });

  const degreeValue = watch('degree');
  const fieldValue = watch('field');
  const startValue = watch('start');
  const endValue = watch('end');
  const fieldOptions = fieldOptionsFor(degreeValue);

  const startAdd = () => {
    reset(emptyValues);
    setEditingId(null);
    setDeleteError(null);
    setIsAdding(true);
  };

  const startEdit = (evidenceId: string) => {
    const item = profile.education.find((e) => e.evidenceId === evidenceId);
    if (!item) return;
    setEditingId(evidenceId);
    setDeleteError(null);
    setIsAdding(true);
    const degree = toPicklistValue(DEGREE_OPTIONS, item.degree);
    const field = toPicklistValue(fieldOptionsFor(degree.select), item.field);
    reset({
      institution: item.institution ?? '',
      degree: degree.select,
      degreeOther: degree.other,
      field: field.select,
      fieldOther: field.other,
      start: item.start ?? '',
      end: item.end ?? '',
      grade: item.grade ?? '',
    });
  };

  const cancelEdit = () => {
    setEditingId(null);
    setIsAdding(false);
    setFormError(null);
    reset(emptyValues);
  };

  /** The dropdown just narrowed, possibly out from under a field-of-study value that no
   *  longer applies — clear it rather than silently keep a mismatched selection. Wired to
   *  the select's own onChange (not a `watch` effect) so it never fires from the
   *  reset()-with-both-values calls above (startEdit/startAdd/cancelEdit). */
  const handleDegreeChange = (newDegree: string) => {
    const currentField = getValues('field');
    if (currentField && !fieldOptionsFor(newDegree).includes(currentField)) {
      setValue('field', '');
      setValue('fieldOther', '');
    }
  };

  const onSubmit = async (values: FormValues) => {
    setFormError(null);
    const degree = values.degree === OTHER ? (values.degreeOther ?? '').trim() : values.degree;
    const field = values.field === OTHER ? (values.fieldOther ?? '').trim() || undefined : values.field || undefined;
    const payload = {
      institution: values.institution,
      degree,
      field,
      start: values.start,
      end: values.end,
      grade: values.grade,
    };
    try {
      const updated = editingId
        ? await profileApi.updateEducation(editingId, payload)
        : await profileApi.addEducation(payload);
      onChanged(updated);
      cancelEdit();
    } catch (error) {
      // Stay open, keep whatever the user typed, surface why it didn't save.
      setFormError(error);
    }
  };

  const handleDelete = async (evidenceId: string) => {
    setDeletingId(evidenceId);
    setDeleteError(null);
    try {
      const updated = await profileApi.deleteEducation(evidenceId);
      onChanged(updated);
      if (editingId === evidenceId) cancelEdit();
    } catch (error) {
      // The card stays put and re-enables — a silently-vanishing-then-reappearing card would
      // be far more confusing than an explicit error.
      setDeleteError(error);
    } finally {
      setDeletingId(null);
    }
  };

  const degreeRegister = register('degree');
  const form = (
    <>
      <h3 className="text-sm font-semibold text-ink">{editingId ? 'Edit education' : 'Add education'}</h3>
      <form className="mt-4 space-y-4" onSubmit={handleSubmit(onSubmit)} noValidate>
        {formError !== null ? <ErrorBanner error={formError} /> : null}
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Institution" error={errors.institution?.message} {...register('institution')} />
          <div className="space-y-4">
            <Select
              label="Degree"
              error={errors.degree?.message}
              {...degreeRegister}
              onChange={(e) => {
                degreeRegister.onChange(e);
                handleDegreeChange(e.target.value);
              }}
            >
              <option value="" disabled>
                Select degree
              </option>
              {DEGREE_OPTIONS.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </Select>
            {degreeValue === OTHER && (
              <TextField
                label="Specify your degree"
                error={errors.degreeOther?.message}
                {...register('degreeOther')}
              />
            )}
          </div>
          <div className="space-y-4">
            <Select label="Field of study" {...register('field')}>
              <option value="">Select field of study (optional)</option>
              {fieldOptions.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </Select>
            {fieldValue === OTHER && (
              <TextField label="Specify field of study" {...register('fieldOther')} />
            )}
          </div>
          <TextField label="Grade / GPA" {...register('grade')} />
          <MonthField
            label="Start"
            value={startValue}
            onChange={(v) => setValue('start', v, { shouldValidate: true })}
            max={endValue || undefined}
          />
          <MonthField
            label="End"
            value={endValue}
            onChange={(v) => setValue('end', v, { shouldValidate: true })}
            min={startValue || undefined}
            hint="Leave blank if still ongoing"
          />
        </div>
        <div className="flex gap-3">
          <Button type="submit" variant="accent" loading={isSubmitting} disabled={isSubmitting}>
            {editingId ? 'Save changes' : 'Add education'}
          </Button>
          <Button type="button" variant="ghost" onClick={cancelEdit} disabled={isSubmitting}>
            Cancel
          </Button>
        </div>
      </form>
    </>
  );

  const isOpen = isAdding || editingId !== null;
  const isEmpty = profile.education.length === 0;

  // Exactly one "add" affordance at a time: EmptyState's own action button while there's
  // truly nothing on screen and nothing being added, otherwise the list (if any) plus
  // SectionEditorToggle's trigger/form — never both "+ Add Education" buttons together.
  if (isEmpty && !isOpen) {
    return (
      <div className="space-y-4">
        <EmptyState
          icon={<GraduationCapIcon className="h-5 w-5" />}
          title="No education added yet."
          hint="Your degrees and coursework help tailor generated resumes to a role's requirements."
          action={
            <Button type="button" variant="secondary" onClick={startAdd}>
              + Add Education
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
          {profile.education.map((item) => (
            <div
              key={item.evidenceId}
              className={`animate-card-in transition-all duration-150 ${
                deletingId === item.evidenceId ? 'scale-[0.98] opacity-50' : 'scale-100 opacity-100'
              }`}
            >
              <RecordCard
                title={`${item.degree ?? ''}${item.field ? ` in ${item.field}` : ''} — ${item.institution ?? ''}`}
                meta={`${item.start ?? ''} – ${item.end || 'Present'}${item.grade ? ` · ${item.grade}` : ''}`}
                onEdit={() => startEdit(item.evidenceId)}
                onDelete={() => handleDelete(item.evidenceId)}
                deleting={deletingId === item.evidenceId}
                removeLabel="Remove education entry"
              >
                {item.description && <p className="mt-2 text-sm text-ink-muted">{item.description}</p>}
              </RecordCard>
            </div>
          ))}
        </div>
      )}
      <SectionEditorToggle open={isOpen} addLabel="Add Education" onOpen={startAdd}>
        {form}
      </SectionEditorToggle>
    </div>
  );
}
