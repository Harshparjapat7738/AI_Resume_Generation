import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { TextField } from '@/components/ui/TextField';
import * as profileApi from '@/services/profileApi';
import type { ProfileResponse } from '@/services/profileApi';

const schema = z.object({
  fullName: z.string().trim().min(1, 'Enter your full name').max(200),
  headline: z.string().trim().max(200).optional(),
  email: z.string().trim().email('Enter a valid email address').optional().or(z.literal('')),
  phone: z.string().trim().max(40).optional(),
});

type FormValues = z.infer<typeof schema>;

export function PersonalInfoForm({
  profile,
  onSaved,
  submitLabel = 'Save',
}: {
  profile: ProfileResponse;
  onSaved: (profile: ProfileResponse) => void;
  submitLabel?: string;
}) {
  const [submitError, setSubmitError] = useState<unknown>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      fullName: profile.personalInformation.fullName ?? '',
      headline: profile.personalInformation.headline ?? '',
      email: profile.personalInformation.email ?? '',
      phone: profile.personalInformation.phone ?? '',
    },
  });

  const onSubmit = async (values: FormValues) => {
    setSubmitError(null);
    try {
      const updated = await profileApi.updatePersonalInformation({
        fullName: values.fullName,
        headline: values.headline,
        email: values.email,
        phone: values.phone,
        links: profile.personalInformation.links,
      });
      onSaved(updated);
    } catch (error) {
      setSubmitError(error);
    }
  };

  return (
    <form className="space-y-4" onSubmit={handleSubmit(onSubmit)} noValidate>
      {submitError !== null ? <ErrorBanner error={submitError} /> : null}
      <TextField label="Full name" error={errors.fullName?.message} {...register('fullName')} />
      <TextField
        label="Professional headline"
        placeholder="Backend Engineer"
        error={errors.headline?.message}
        {...register('headline')}
      />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="Email" type="email" error={errors.email?.message} {...register('email')} />
        <TextField label="Phone" error={errors.phone?.message} {...register('phone')} />
      </div>
      <Button type="submit" loading={isSubmitting}>
        {submitLabel}
      </Button>
    </form>
  );
}
