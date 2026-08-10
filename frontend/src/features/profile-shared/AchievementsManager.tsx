import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { TextArea } from '@/components/ui/TextArea';
import { TextField } from '@/components/ui/TextField';
import * as profileApi from '@/services/profileApi';
import type { ProfileResponse } from '@/services/profileApi';

const schema = z.object({
  title: z.string().trim().min(1, 'Enter a title').max(200),
  description: z.string().trim().max(1000).optional(),
  date: z.string().trim().max(20).optional(),
});

type FormValues = z.infer<typeof schema>;

export function AchievementsManager({
  profile,
  onChanged,
}: {
  profile: ProfileResponse;
  onChanged: (profile: ProfileResponse) => void;
}) {
  const [formError, setFormError] = useState<unknown>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const startEdit = (evidenceId: string) => {
    const item = profile.achievements.find((a) => a.evidenceId === evidenceId);
    if (!item) return;
    setEditingId(evidenceId);
    reset({ title: item.title ?? '', description: item.description ?? '', date: item.date ?? '' });
  };

  const cancelEdit = () => {
    setEditingId(null);
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
    try {
      onChanged(await profileApi.deleteAchievement(evidenceId));
      if (editingId === evidenceId) cancelEdit();
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <div className="space-y-6">
      <div className="space-y-4">
        {profile.achievements.length === 0 && (
          <p className="text-sm text-ink-faint">No achievements added yet — awards, competitions, publications, leadership.</p>
        )}
        {profile.achievements.map((item) => (
          <Card key={item.evidenceId} className="!p-5">
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="text-sm font-medium text-ink">{item.title}</p>
                <p className="mt-0.5 text-xs text-ink-faint">
                  {item.date} · {item.evidenceId}
                </p>
                {item.description && <p className="mt-2 text-sm text-ink-muted">{item.description}</p>}
              </div>
              <div className="flex shrink-0 gap-3 text-xs">
                <button type="button" onClick={() => startEdit(item.evidenceId)} className="text-ink-faint hover:text-ink">
                  Edit
                </button>
                <button
                  type="button"
                  onClick={() => handleDelete(item.evidenceId)}
                  disabled={deletingId === item.evidenceId}
                  className="text-ink-faint hover:text-rose"
                >
                  Remove
                </button>
              </div>
            </div>
          </Card>
        ))}
      </div>

      <Card>
        <h3 className="text-sm font-semibold text-ink">{editingId ? 'Edit achievement' : 'Add achievement'}</h3>
        <form className="mt-4 space-y-4" onSubmit={handleSubmit(onSubmit)} noValidate>
          {formError !== null ? <ErrorBanner error={formError} /> : null}
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField label="Title" placeholder="Best Hackathon Project 2022" error={errors.title?.message} {...register('title')} />
            <TextField label="Date (optional)" placeholder="2022-11" {...register('date')} />
          </div>
          <TextArea label="Description (optional)" rows={3} {...register('description')} />
          <div className="flex gap-3">
            <Button type="submit" variant="secondary" loading={isSubmitting}>
              {editingId ? 'Save changes' : 'Add achievement'}
            </Button>
            {editingId && (
              <Button type="button" variant="ghost" onClick={cancelEdit}>
                Cancel
              </Button>
            )}
          </div>
        </form>
      </Card>
    </div>
  );
}
