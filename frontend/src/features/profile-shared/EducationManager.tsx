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
  institution: z.string().trim().min(1, 'Enter the institution name').max(200),
  degree: z.string().trim().min(1, 'Enter your degree').max(200),
  field: z.string().trim().max(200).optional(),
  start: z.string().trim().max(20).optional(),
  end: z.string().trim().max(20).optional(),
  grade: z.string().trim().max(40).optional(),
  description: z.string().trim().max(1000).optional(),
});

type FormValues = z.infer<typeof schema>;

export function EducationManager({
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
    const item = profile.education.find((e) => e.evidenceId === evidenceId);
    if (!item) return;
    setEditingId(evidenceId);
    reset({
      institution: item.institution ?? '',
      degree: item.degree ?? '',
      field: item.field ?? '',
      start: item.start ?? '',
      end: item.end ?? '',
      grade: item.grade ?? '',
      description: item.description ?? '',
    });
  };

  const cancelEdit = () => {
    setEditingId(null);
    reset({ institution: '', degree: '', field: '', start: '', end: '', grade: '', description: '' });
  };

  const onSubmit = async (values: FormValues) => {
    setFormError(null);
    try {
      const updated = editingId
        ? await profileApi.updateEducation(editingId, values)
        : await profileApi.addEducation(values);
      onChanged(updated);
      cancelEdit();
    } catch (error) {
      setFormError(error);
    }
  };

  const handleDelete = async (evidenceId: string) => {
    setDeletingId(evidenceId);
    try {
      onChanged(await profileApi.deleteEducation(evidenceId));
      if (editingId === evidenceId) cancelEdit();
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <div className="space-y-6">
      <div className="space-y-4">
        {profile.education.length === 0 && (
          <p className="text-sm text-ink-faint">No education added yet.</p>
        )}
        {profile.education.map((item) => (
          <Card key={item.evidenceId} className="!p-5">
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="text-sm font-medium text-ink">
                  {item.degree}
                  {item.field ? ` in ${item.field}` : ''} — {item.institution}
                </p>
                <p className="mt-0.5 text-xs text-ink-faint">
                  {item.start} – {item.end || 'Present'}
                  {item.grade ? ` · ${item.grade}` : ''} · {item.evidenceId}
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
        <h3 className="text-sm font-semibold text-ink">{editingId ? 'Edit education' : 'Add education'}</h3>
        <form className="mt-4 space-y-4" onSubmit={handleSubmit(onSubmit)} noValidate>
          {formError !== null ? <ErrorBanner error={formError} /> : null}
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField label="Institution" error={errors.institution?.message} {...register('institution')} />
            <TextField label="Degree" placeholder="BSc" error={errors.degree?.message} {...register('degree')} />
            <TextField label="Field of study" placeholder="Computer Science" {...register('field')} />
            <TextField label="Grade / GPA" {...register('grade')} />
            <TextField label="Start (e.g. 2018-09)" {...register('start')} />
            <TextField label="End (e.g. 2022-06)" {...register('end')} />
          </div>
          <TextArea label="Description (optional)" rows={3} {...register('description')} />
          <div className="flex gap-3">
            <Button type="submit" variant="secondary" loading={isSubmitting}>
              {editingId ? 'Save changes' : 'Add education'}
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
