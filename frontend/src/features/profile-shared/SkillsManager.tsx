import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { TextField } from '@/components/ui/TextField';
import * as profileApi from '@/services/profileApi';
import type { ProfileResponse } from '@/services/profileApi';

const suggestedCategories = [
  'Programming Languages',
  'Frameworks',
  'Databases',
  'Cloud',
  'DevOps',
  'Tools',
  'Other',
];

const schema = z.object({
  name: z.string().trim().min(1, 'Enter a skill').max(100),
  category: z.string().trim().max(60).optional(),
  proficiency: z.string().trim().max(40).optional(),
  yearsOfExperience: z.coerce.number().int().min(0).max(70).optional().or(z.literal('')),
});

type FormValues = z.infer<typeof schema>;

export function SkillsManager({
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
    const item = profile.skills.find((s) => s.evidenceId === evidenceId);
    if (!item) return;
    setEditingId(evidenceId);
    reset({
      name: item.name ?? '',
      category: item.category ?? '',
      proficiency: item.proficiency ?? '',
      yearsOfExperience: item.yearsOfExperience ?? undefined,
    });
  };

  const cancelEdit = () => {
    setEditingId(null);
    reset({ name: '', category: '', proficiency: '', yearsOfExperience: undefined });
  };

  const onSubmit = async (values: FormValues) => {
    setFormError(null);
    const input = {
      name: values.name,
      category: values.category,
      proficiency: values.proficiency,
      yearsOfExperience: values.yearsOfExperience === '' ? undefined : values.yearsOfExperience,
    };
    try {
      const updated = editingId
        ? await profileApi.updateSkill(editingId, input)
        : await profileApi.addSkill(input);
      onChanged(updated);
      cancelEdit();
    } catch (error) {
      setFormError(error);
    }
  };

  const handleDelete = async (evidenceId: string) => {
    setDeletingId(evidenceId);
    try {
      onChanged(await profileApi.deleteSkill(evidenceId));
      if (editingId === evidenceId) cancelEdit();
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap gap-2">
        {profile.skills.length === 0 && <p className="text-sm text-ink-faint">No skills added yet.</p>}
        {profile.skills.map((skill) => (
          <div
            key={skill.evidenceId}
            className="flex items-center gap-2 rounded-full border border-border bg-surface py-1.5 pl-3.5 pr-2 text-sm"
          >
            <span className="text-ink">{skill.name}</span>
            {skill.category && <span className="text-xs text-ink-faint">· {skill.category}</span>}
            <button type="button" onClick={() => startEdit(skill.evidenceId)} className="ml-1 text-xs text-ink-faint hover:text-ink">
              Edit
            </button>
            <button
              type="button"
              onClick={() => handleDelete(skill.evidenceId)}
              disabled={deletingId === skill.evidenceId}
              className="text-xs text-ink-faint hover:text-rose"
            >
              ✕
            </button>
          </div>
        ))}
      </div>

      <Card>
        <h3 className="text-sm font-semibold text-ink">{editingId ? 'Edit skill' : 'Add skill'}</h3>
        <form className="mt-4 space-y-4" onSubmit={handleSubmit(onSubmit)} noValidate>
          {formError !== null ? <ErrorBanner error={formError} /> : null}
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField label="Skill" placeholder="Kubernetes" error={errors.name?.message} {...register('name')} />
            <TextField
              label="Category (optional)"
              list="skill-categories"
              placeholder="DevOps"
              {...register('category')}
            />
            <datalist id="skill-categories">
              {suggestedCategories.map((c) => (
                <option key={c} value={c} />
              ))}
            </datalist>
            <TextField label="Proficiency (optional)" placeholder="Advanced" {...register('proficiency')} />
            <TextField
              label="Years of experience (optional)"
              type="number"
              min={0}
              max={70}
              error={errors.yearsOfExperience?.message}
              {...register('yearsOfExperience')}
            />
          </div>
          <div className="flex gap-3">
            <Button type="submit" variant="secondary" loading={isSubmitting}>
              {editingId ? 'Save changes' : 'Add skill'}
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
