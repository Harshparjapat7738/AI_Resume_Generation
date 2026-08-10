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
  name: z.string().trim().min(1, 'Enter the project name').max(200),
  description: z.string().trim().max(1000).optional(),
  role: z.string().trim().max(100).optional(),
  technologies: z.string().trim().optional(),
  achievements: z.string().trim().optional(),
  githubUrl: z.string().trim().max(300).optional(),
  liveUrl: z.string().trim().max(300).optional(),
  start: z.string().trim().max(20).optional(),
  end: z.string().trim().max(20).optional(),
});

type FormValues = z.infer<typeof schema>;

const splitLines = (value: string | undefined) =>
  (value ?? '').split('\n').map((line) => line.trim()).filter(Boolean);
const splitCommas = (value: string | undefined) =>
  (value ?? '').split(',').map((v) => v.trim()).filter(Boolean);

export function ProjectsManager({
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
    const item = profile.projects.find((p) => p.evidenceId === evidenceId);
    if (!item) return;
    setEditingId(evidenceId);
    reset({
      name: item.name ?? '',
      description: item.description ?? '',
      role: item.role ?? '',
      technologies: item.technologies.join(', '),
      achievements: item.metrics.join('\n'),
      githubUrl: item.githubUrl ?? '',
      liveUrl: item.liveUrl ?? '',
      start: item.start ?? '',
      end: item.end ?? '',
    });
  };

  const cancelEdit = () => {
    setEditingId(null);
    reset({
      name: '', description: '', role: '', technologies: '', achievements: '',
      githubUrl: '', liveUrl: '', start: '', end: '',
    });
  };

  const onSubmit = async (values: FormValues) => {
    setFormError(null);
    const input = {
      name: values.name,
      description: values.description,
      role: values.role,
      technologies: splitCommas(values.technologies),
      metrics: splitLines(values.achievements),
      githubUrl: values.githubUrl,
      liveUrl: values.liveUrl,
      start: values.start,
      end: values.end,
    };
    try {
      const updated = editingId
        ? await profileApi.updateProject(editingId, input)
        : await profileApi.addProject(input);
      onChanged(updated);
      cancelEdit();
    } catch (error) {
      setFormError(error);
    }
  };

  const handleDelete = async (evidenceId: string) => {
    setDeletingId(evidenceId);
    try {
      onChanged(await profileApi.deleteProject(evidenceId));
      if (editingId === evidenceId) cancelEdit();
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <div className="space-y-6">
      <div className="space-y-4">
        {profile.projects.length === 0 && <p className="text-sm text-ink-faint">No projects added yet.</p>}
        {profile.projects.map((item) => (
          <Card key={item.evidenceId} className="!p-5">
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="text-sm font-medium text-ink">
                  {item.name}
                  {item.role ? ` — ${item.role}` : ''}
                </p>
                <p className="mt-0.5 text-xs text-ink-faint">
                  {item.start} – {item.end || 'Present'} · {item.evidenceId}
                </p>
                {item.description && <p className="mt-2 text-sm text-ink-muted">{item.description}</p>}
                {item.technologies.length > 0 && (
                  <p className="mt-1 text-xs text-ink-faint">{item.technologies.join(', ')}</p>
                )}
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
        <h3 className="text-sm font-semibold text-ink">{editingId ? 'Edit project' : 'Add project'}</h3>
        <form className="mt-4 space-y-4" onSubmit={handleSubmit(onSubmit)} noValidate>
          {formError !== null ? <ErrorBanner error={formError} /> : null}
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField label="Project name" error={errors.name?.message} {...register('name')} />
            <TextField label="Your role" placeholder="Lead developer" {...register('role')} />
            <TextField label="Start (e.g. 2022-01)" {...register('start')} />
            <TextField label="End (e.g. 2023-01)" {...register('end')} />
            <TextField label="GitHub URL" type="url" {...register('githubUrl')} />
            <TextField label="Live URL" type="url" {...register('liveUrl')} />
          </div>
          <TextArea label="Description" rows={3} {...register('description')} />
          <TextArea label="Key achievements — one per line (optional)" rows={2} {...register('achievements')} />
          <TextField label="Technologies (comma-separated)" placeholder="Go, PostgreSQL" {...register('technologies')} />
          <div className="flex gap-3">
            <Button type="submit" variant="secondary" loading={isSubmitting}>
              {editingId ? 'Save changes' : 'Add project'}
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
