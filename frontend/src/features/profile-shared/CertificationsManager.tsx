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

const schema = z.object({
  name: z.string().trim().min(1, 'Enter the certification name').max(200),
  issuer: z.string().trim().min(1, 'Enter the issuer').max(200),
  issuedOn: z.string().trim().max(20).optional(),
  expiresOn: z.string().trim().max(20).optional(),
  credentialId: z.string().trim().max(100).optional(),
  credentialUrl: z.string().trim().max(300).optional(),
});

type FormValues = z.infer<typeof schema>;

export function CertificationsManager({
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
    const item = profile.certifications.find((c) => c.evidenceId === evidenceId);
    if (!item) return;
    setEditingId(evidenceId);
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
    try {
      onChanged(await profileApi.deleteCertification(evidenceId));
      if (editingId === evidenceId) cancelEdit();
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <div className="space-y-6">
      <div className="space-y-4">
        {profile.certifications.length === 0 && (
          <p className="text-sm text-ink-faint">No certifications added yet.</p>
        )}
        {profile.certifications.map((item) => (
          <Card key={item.evidenceId} className="!p-5">
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="text-sm font-medium text-ink">{item.name}</p>
                <p className="mt-0.5 text-xs text-ink-faint">
                  {item.issuer} · {item.issuedOn}
                  {item.expiresOn ? ` – ${item.expiresOn}` : ''} · {item.evidenceId}
                </p>
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
        <h3 className="text-sm font-semibold text-ink">{editingId ? 'Edit certification' : 'Add certification'}</h3>
        <form className="mt-4 space-y-4" onSubmit={handleSubmit(onSubmit)} noValidate>
          {formError !== null ? <ErrorBanner error={formError} /> : null}
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField label="Name" placeholder="AWS Certified Solutions Architect" error={errors.name?.message} {...register('name')} />
            <TextField label="Issuer" placeholder="Amazon Web Services" error={errors.issuer?.message} {...register('issuer')} />
            <TextField label="Issued (e.g. 2023-05)" {...register('issuedOn')} />
            <TextField label="Expires (optional)" {...register('expiresOn')} />
            <TextField label="Credential ID (optional)" {...register('credentialId')} />
            <TextField label="Credential URL (optional)" type="url" {...register('credentialUrl')} />
          </div>
          <div className="flex gap-3">
            <Button type="submit" variant="secondary" loading={isSubmitting}>
              {editingId ? 'Save changes' : 'Add certification'}
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
