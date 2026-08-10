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
  company: z.string().trim().min(1, 'Enter the company name').max(200),
  title: z.string().trim().min(1, 'Enter your job title').max(200),
  start: z.string().trim().min(1, 'Enter a start date (e.g. 2021-03)').max(20),
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
 */
export function ExperienceManager({
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
  } = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: { current: false } });

  const startEdit = (evidenceId: string) => {
    const item = profile.experiences.find((e) => e.evidenceId === evidenceId);
    if (!item) return;
    setEditingId(evidenceId);
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
    try {
      const updated = await profileApi.deleteExperience(evidenceId);
      onChanged(updated);
      if (editingId === evidenceId) cancelEdit();
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <div className="space-y-6">
      <div className="space-y-4">
        {profile.experiences.length === 0 && (
          <p className="text-sm text-ink-faint">No experience added yet — add at least one below.</p>
        )}
        {profile.experiences.map((exp) => (
          <Card key={exp.evidenceId} className="!p-5">
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="text-sm font-medium text-ink">
                  {exp.title} — {exp.company}
                </p>
                <p className="mt-0.5 text-xs text-ink-faint">
                  {exp.start} – {exp.current ? 'Present' : exp.end} · {exp.evidenceId}
                </p>
                <ul className="mt-2 list-inside list-disc space-y-1 text-sm text-ink-muted">
                  {exp.bullets.map((bullet, i) => (
                    <li key={i}>{bullet}</li>
                  ))}
                </ul>
              </div>
              <div className="flex shrink-0 gap-3 text-xs">
                <button
                  type="button"
                  onClick={() => startEdit(exp.evidenceId)}
                  className="text-ink-faint transition-colors hover:text-ink"
                >
                  Edit
                </button>
                <button
                  type="button"
                  onClick={() => handleDelete(exp.evidenceId)}
                  disabled={deletingId === exp.evidenceId}
                  className="text-ink-faint transition-colors hover:text-rose"
                >
                  Remove
                </button>
              </div>
            </div>
          </Card>
        ))}
      </div>

      <Card>
        <h3 className="text-sm font-semibold text-ink">{editingId ? 'Edit experience' : 'Add experience'}</h3>
        <form className="mt-4 space-y-4" onSubmit={handleSubmit(onSubmit)} noValidate>
          {formError !== null ? <ErrorBanner error={formError} /> : null}
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField label="Company" error={errors.company?.message} {...register('company')} />
            <TextField label="Job title" error={errors.title?.message} {...register('title')} />
            <TextField label="Start (e.g. 2021-03)" error={errors.start?.message} {...register('start')} />
            <TextField label="End (e.g. 2024-01)" error={errors.end?.message} {...register('end')} />
          </div>
          <label className="flex items-center gap-2 text-sm text-ink-muted">
            <input type="checkbox" className="h-4 w-4 rounded border-border" {...register('current')} />
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
            <Button type="submit" variant="secondary" loading={isSubmitting}>
              {editingId ? 'Save changes' : 'Add experience'}
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
