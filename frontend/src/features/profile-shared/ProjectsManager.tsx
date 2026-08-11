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
import { FolderIcon } from '@/features/profile/components/icons';
import { RecordCard } from '@/features/profile/components/RecordCard';
import { SectionEditorToggle } from '@/features/profile/components/SectionEditorToggle';

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

/** One card per project, collapsed behind "+ Add Project" when there's nothing (or nothing
 *  being edited) to show — same pattern as EducationManager. */
export function ProjectsManager({
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

  const startValue = watch('start');
  const endValue = watch('end');

  const startAdd = () => {
    reset({
      name: '', description: '', role: '', technologies: '', achievements: '',
      githubUrl: '', liveUrl: '', start: '', end: '',
    });
    setEditingId(null);
    setDeleteError(null);
    setIsAdding(true);
  };

  const startEdit = (evidenceId: string) => {
    const item = profile.projects.find((p) => p.evidenceId === evidenceId);
    if (!item) return;
    setEditingId(evidenceId);
    setDeleteError(null);
    setIsAdding(true);
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
    setIsAdding(false);
    setFormError(null);
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
    setDeleteError(null);
    try {
      const updated = await profileApi.deleteProject(evidenceId);
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
      <h3 className="text-sm font-semibold text-ink">{editingId ? 'Edit project' : 'Add project'}</h3>
      <form className="mt-4 space-y-4" onSubmit={handleSubmit(onSubmit)} noValidate>
        {formError !== null ? <ErrorBanner error={formError} /> : null}
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Project name" error={errors.name?.message} {...register('name')} />
          <TextField label="Your role" placeholder="Lead developer" {...register('role')} />
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
          />
          <TextField label="GitHub URL" type="url" {...register('githubUrl')} />
          <TextField label="Live URL" type="url" {...register('liveUrl')} />
        </div>
        <TextArea label="Description" rows={3} {...register('description')} />
        <TextArea label="Key achievements — one per line (optional)" rows={2} {...register('achievements')} />
        <TextField label="Technologies (comma-separated)" placeholder="Go, PostgreSQL" {...register('technologies')} />
        <div className="flex gap-3">
          <Button type="submit" variant="accent" loading={isSubmitting} disabled={isSubmitting}>
            {editingId ? 'Save changes' : 'Add project'}
          </Button>
          <Button type="button" variant="ghost" onClick={cancelEdit} disabled={isSubmitting}>
            Cancel
          </Button>
        </div>
      </form>
    </>
  );

  const isOpen = isAdding || editingId !== null;
  const isEmpty = profile.projects.length === 0;

  if (isEmpty && !isOpen) {
    return (
      <div className="space-y-4">
        <EmptyState
          icon={<FolderIcon className="h-5 w-5" />}
          title="No projects added yet."
          hint="Projects help employers understand what you have built."
          action={
            <Button type="button" variant="secondary" onClick={startAdd}>
              + Add Project
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
          {profile.projects.map((item) => (
            <div
              key={item.evidenceId}
              className={`animate-card-in transition-all duration-150 ${
                deletingId === item.evidenceId ? 'scale-[0.98] opacity-50' : 'scale-100 opacity-100'
              }`}
            >
              <RecordCard
                title={item.role ? `${item.name} — ${item.role}` : item.name}
                meta={`${item.start ?? ''} – ${item.end || 'Present'}`}
                tags={item.technologies}
                onEdit={() => startEdit(item.evidenceId)}
                onDelete={() => handleDelete(item.evidenceId)}
                deleting={deletingId === item.evidenceId}
                removeLabel="Remove project entry"
              >
                {item.description && <p className="mt-2 text-sm text-ink-muted">{item.description}</p>}
              </RecordCard>
            </div>
          ))}
        </div>
      )}
      <SectionEditorToggle open={isOpen} addLabel="Add Project" onOpen={startAdd}>
        {form}
      </SectionEditorToggle>
    </div>
  );
}
