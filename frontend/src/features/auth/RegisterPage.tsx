import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { TextField } from '@/components/ui/TextField';
import { login, me, register as registerAccount } from '@/services/authApi';
import { setAccessToken } from '@/services/apiClient';
import { useSessionActions } from '@/services/session';
import { AuthLayout } from './AuthLayout';

const schema = z.object({
  displayName: z.string().trim().min(1, 'Enter your name').max(200),
  email: z.string().trim().email('Enter a valid email address'),
  password: z.string().min(8, 'Must be at least 8 characters').max(128),
});

type FormValues = z.infer<typeof schema>;

export function RegisterPage() {
  const navigate = useNavigate();
  const { setSession } = useSessionActions();
  const [submitError, setSubmitError] = useState<unknown>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const onSubmit = async (values: FormValues) => {
    setSubmitError(null);
    try {
      await registerAccount(values);
      const tokens = await login({ email: values.email, password: values.password });
      setAccessToken(tokens.accessToken);
      setSession(await me());
      // A brand-new account has no profile yet — always onboard first, never straight into
      // a generation workflow (see docs/API_INTEGRATION.md, authentication flow).
      navigate('/onboarding', { replace: true });
    } catch (error) {
      setSubmitError(error);
    }
  };

  return (
    <AuthLayout title="Create your account" subtitle="One verified profile powers every application.">
      <form className="space-y-4" onSubmit={handleSubmit(onSubmit)} noValidate>
        {submitError !== null ? <ErrorBanner error={submitError} /> : null}
        <TextField
          label="Full name"
          autoComplete="name"
          error={errors.displayName?.message}
          {...register('displayName')}
        />
        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          error={errors.email?.message}
          {...register('email')}
        />
        <TextField
          label="Password"
          type="password"
          autoComplete="new-password"
          error={errors.password?.message}
          {...register('password')}
        />
        <Button type="submit" className="w-full" loading={isSubmitting}>
          Create account
        </Button>
      </form>
      <p className="mt-6 text-center text-sm text-ink-faint">
        Already have an account?{' '}
        <Link to="/login" className="text-ink underline underline-offset-2">
          Log in
        </Link>
      </p>
    </AuthLayout>
  );
}
