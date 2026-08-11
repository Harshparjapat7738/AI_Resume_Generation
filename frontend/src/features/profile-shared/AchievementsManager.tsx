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
import { TrophyIcon } from '@/features/profile/components/icons';
import { RecordCard } from '@/features/profile/components/RecordCard';
import { SectionEditorToggle } from '@/features/profile/components/SectionEditorToggle';

const schema = z.object({
  title: z.string().trim().min(1, 'Enter a title').max(200),
  description: z.string().trim().max(1000).optional(),
  date: z.string().trim().max(20).optional(),
});

type FormValues = z.infer<typeof schema>;

/** One card per achievement, collapsed behind "+ Add Achievement" when there's nothing (or
 *  nothing being edited) to show — same pattern as EducationManager. */
export function AchievementsManager({
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
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const dateValue = watch('date');

  const startAdd = () => {
    reset({ title: '', description: '', date: '' });
    setEditingId(null);
    setDeleteError(null);
    setIsAdding(true);
  };

  const startEdit = (evidenceId: string) => {
    const item = profile.achievements.find((a) => a.evidenceId === evidenceId);
    if (!item) return;
    setEditingId(evidenceId);
    setDeleteError(null);
    setIsAdding(true);
    reset({ title: item.title ?? '', description: item.description ?? '', date: item.date ?? '' });
  };

  const cancelEdit = () => {
    setEditingId(null);
    setIsAdding(false);
    setFormError(null);
    reset({ title: '', description: '', date: '' });
  };

  const onSubmit = async (values: FormValues) => {
    setFormError(null);
    try {
      const updated = editingId
        ? await profileApi.updateAchievement(editingId, values)
        : await profileApi.addAchievement(values);
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
      const updated = await profileApi.deleteAchievement(evidenceId);
      onChanged(updated);
      if (editingId === evidenceId) cancelEdit();
    } catch (error) {
      setDeleteError(error);
    } finally {
      setDeletingId(null);
    }
  };

  const form = (
    <>
      <h3 className="text-sm font-semibold text-ink">{editingId ? 'Edit achievement' : 'Add achievement'}</h3>
      <form className="mt-4 space-y-4" onSubmit={handleSubmit(onSubmit)} noValidate>
        {formError !== null ? <ErrorBanner error={formError} /> : null}
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Title" placeholder="Best Hackathon Project 2022" error={errors.title?.message} {...register('title')} />
          <MonthField label="Date (optional)" value={dateValue} onChange={(v) => setValue('date', v, { shouldValidate: true })} />
        </div>
        <TextArea label="Description (optional)" rows={3} {...register('description')} />
        <div className="flex gap-3">
          <Button type="submit" variant="accent" loading={isSubmitting} disabled={isSubmitting}>
            {editingId ? 'Save changes' : 'Add achievement'}
          </Button>
          <Button type="button" variant="ghost" onClick={cancelEdit} disabled={isSubmitting}>
            Cancel
          </Button>
        </div>
      </form>
    </>
  );

  const isOpen = isAdding || editingId !== null;
  const isEmpty = profile.achievements.length === 0;

  if (isEmpty && !isOpen) {
    return (
      <div className="space-y-4">
        <EmptyState
          icon={<TrophyIcon className="h-5 w-5" />}
          title="No achievements added yet."
          hint="Awards, competitions, publications, leadership — anything worth citing."
          action={
            <Button type="button" variant="secondary" onClick={startAdd}>
              + Add Achievement
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
          {profile.achievements.map((item) => (
            <div
              key={item.evidenceId}
              className={`animate-card-in transition-all duration-150 ${
                deletingId === item.evidenceId ? 'scale-[0.98] opacity-50' : 'scale-100 opacity-100'
              }`}
            >
              <RecordCard
                title={
                  <span className="inline-flex items-center gap-2">
                    <span aria-hidden="true">🏆</span>
                    {item.title}
                  </span>
                }
                meta={item.date ?? undefined}
                onEdit={() => startEdit(item.evidenceId)}
                onDelete={() => handleDelete(item.evidenceId)}
                deleting={deletingId === item.evidenceId}
                removeLabel="Remove achievement entry"
              >
                {item.description && <p className="mt-2 text-sm text-ink-muted">{item.description}</p>}
              </RecordCard>
            </div>
          ))}
        </div>
      )}
      <SectionEditorToggle open={isOpen} addLabel="Add Achievement" onOpen={startAdd}>
        {form}
      </SectionEditorToggle>
    </div>
  );
}
