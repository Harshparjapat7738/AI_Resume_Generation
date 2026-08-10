import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import type { Location } from 'react-router-dom';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { TextField } from '@/components/ui/TextField';
import { login, me } from '@/services/authApi';
import { setAccessToken } from '@/services/apiClient';
import { getProfile, isProfileComplete } from '@/services/profileApi';
import { useSessionActions } from '@/services/session';
import { AuthLayout } from './AuthLayout';

const schema = z.object({
  email: z.string().trim().email('Enter a valid email address'),
  password: z.string().min(1, 'Enter your password'),
});

type FormValues = z.infer<typeof schema>;

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
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
      const tokens = await login(values);
      setAccessToken(tokens.accessToken);
      setSession(await me());

      // New or incomplete profiles always go through onboarding first, and onboarding
      // always finishes at the landing page — never straight into a generation workflow.
      const profile = await getProfile();
      if (!isProfileComplete(profile)) {
        navigate('/onboarding', { replace: true });
        return;
      }
      const redirectTo = (location.state as { from?: Location })?.from?.pathname ?? '/';
      navigate(redirectTo, { replace: true });
    } catch (error) {
      setSubmitError(error);
    }
  };

  return (
    <AuthLayout title="Welcome back" subtitle="Log in to continue where you left off.">
      <form className="space-y-4" onSubmit={handleSubmit(onSubmit)} noValidate>
        {submitError !== null ? <ErrorBanner error={submitError} /> : null}
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
          autoComplete="current-password"
          error={errors.password?.message}
          {...register('password')}
        />
        <Button type="submit" className="w-full" loading={isSubmitting}>
          Log in
        </Button>
      </form>
      <p className="mt-6 text-center text-sm text-ink-faint">
        New to CareerForge AI?{' '}
        <Link to="/register" className="text-ink underline underline-offset-2">
          Create an account
        </Link>
      </p>
    </AuthLayout>
  );
}
