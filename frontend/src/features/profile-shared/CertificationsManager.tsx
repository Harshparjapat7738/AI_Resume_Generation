import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { MonthField } from '@/components/ui/MonthField';
import { TextField } from '@/components/ui/TextField';
import * as profileApi from '@/services/profileApi';
import type { ProfileResponse } from '@/services/profileApi';
import { EmptyState } from '@/components/ui/EmptyState';
import { AwardIcon } from '@/features/profile/components/icons';
import { RecordCard } from '@/features/profile/components/RecordCard';
import { SectionEditorToggle } from '@/features/profile/components/SectionEditorToggle';

const schema = z.object({
  name: z.string().trim().min(1, 'Enter the certification name').max(200),
  issuer: z.string().trim().min(1, 'Enter the issuer').max(200),
  issuedOn: z.string().trim().max(20).optional(),
  expiresOn: z.string().trim().max(20).optional(),
  credentialId: z.string().trim().max(100).optional(),
  credentialUrl: z.string().trim().max(300).optional(),
});

type FormValues = z.infer<typeof schema>;

/** One card per certification, collapsed behind "+ Add Certification" when there's nothing
 *  (or nothing being edited) to show — same pattern as EducationManager. */
export function CertificationsManager({
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

  const issuedOnValue = watch('issuedOn');
  const expiresOnValue = watch('expiresOn');

  const startAdd = () => {
    reset({ name: '', issuer: '', issuedOn: '', expiresOn: '', credentialId: '', credentialUrl: '' });
    setEditingId(null);
    setDeleteError(null);
    setIsAdding(true);
  };

  const startEdit = (evidenceId: string) => {
    const item = profile.certifications.find((c) => c.evidenceId === evidenceId);
    if (!item) return;
    setEditingId(evidenceId);
    setDeleteError(null);
    setIsAdding(true);
    reset({
      name: item.name ?? '',
      issuer: item.issuer ?? '',
      issuedOn: item.issuedOn ?? '',
      expiresOn: item.expiresOn ?? '',
      credentialId: item.credentialId ?? '',
      credentialUrl: item.credentialUrl ?? '',
    });
  };

  const cancelEdit = () => {
    setEditingId(null);
    setIsAdding(false);
    setFormError(null);
    reset({ name: '', issuer: '', issuedOn: '', expiresOn: '', credentialId: '', credentialUrl: '' });
  };

  const onSubmit = async (values: FormValues) => {
    setFormError(null);
    try {
      const updated = editingId
        ? await profileApi.updateCertification(editingId, values)
        : await profileApi.addCertification(values);
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
      const updated = await profileApi.deleteCertification(evidenceId);
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
      <h3 className="text-sm font-semibold text-ink">{editingId ? 'Edit certification' : 'Add certification'}</h3>
      <form className="mt-4 space-y-4" onSubmit={handleSubmit(onSubmit)} noValidate>
        {formError !== null ? <ErrorBanner error={formError} /> : null}
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Name" placeholder="AWS Certified Solutions Architect" error={errors.name?.message} {...register('name')} />
          <TextField label="Issuer" placeholder="Amazon Web Services" error={errors.issuer?.message} {...register('issuer')} />
          <MonthField
            label="Issued"
            value={issuedOnValue}
            onChange={(v) => setValue('issuedOn', v, { shouldValidate: true })}
            max={expiresOnValue || undefined}
          />
          <MonthField
            label="Expires (optional)"
            value={expiresOnValue}
            onChange={(v) => setValue('expiresOn', v, { shouldValidate: true })}
            min={issuedOnValue || undefined}
          />
          <TextField label="Credential ID (optional)" {...register('credentialId')} />
          <TextField label="Credential URL (optional)" type="url" {...register('credentialUrl')} />
        </div>
        <div className="flex gap-3">
          <Button type="submit" variant="accent" loading={isSubmitting} disabled={isSubmitting}>
            {editingId ? 'Save changes' : 'Add certification'}
          </Button>
          <Button type="button" variant="ghost" onClick={cancelEdit} disabled={isSubmitting}>
            Cancel
          </Button>
        </div>
      </form>
    </>
  );

  const isOpen = isAdding || editingId !== null;
  const isEmpty = profile.certifications.length === 0;

  if (isEmpty && !isOpen) {
    return (
      <div className="space-y-4">
        <EmptyState
          icon={<AwardIcon className="h-5 w-5" />}
          title="No certifications added yet."
          hint="Certifications back up claims a resume alone can't prove."
          action={
            <Button type="button" variant="secondary" onClick={startAdd}>
              + Add Certification
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
          {profile.certifications.map((item) => (
            <div
              key={item.evidenceId}
              className={`animate-card-in transition-all duration-150 ${
                deletingId === item.evidenceId ? 'scale-[0.98] opacity-50' : 'scale-100 opacity-100'
              }`}
            >
              <RecordCard
                title={
                  <span className="inline-flex items-center gap-2">
                    <span className="text-mint" aria-hidden="true">✓</span>
                    {item.name}
                  </span>
                }
                meta={`${item.issuer ?? ''} · ${item.issuedOn ?? ''}${item.expiresOn ? ` – ${item.expiresOn}` : ''}`}
                onEdit={() => startEdit(item.evidenceId)}
                onDelete={() => handleDelete(item.evidenceId)}
                deleting={deletingId === item.evidenceId}
                removeLabel="Remove certification entry"
              />
            </div>
          ))}
        </div>
      )}
      <SectionEditorToggle open={isOpen} addLabel="Add Certification" onOpen={startAdd}>
        {form}
      </SectionEditorToggle>
    </div>
  );
}
