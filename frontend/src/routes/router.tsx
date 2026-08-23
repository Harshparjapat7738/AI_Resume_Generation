import { createBrowserRouter, Navigate, redirect } from 'react-router-dom';

/**
 * Route table for the wizard described in the blueprint (§20).
 *
 * The landing page ('/') stays the central page for both anonymous and authenticated
 * visitors — it is never bypassed after login. /onboarding, /generate/*, /results/:id,
 * /dashboard and /profile require an active session — see ProtectedRoute. Login/register
 * always land on /onboarding (new/incomplete profile) or the originally-intended page
 * (existing, complete profile) — never automatically into a generation workflow. See
 * docs/API_INTEGRATION.md, "Authentication flow".
 *
 * Generation flow (no Confirm/Review step — removed entirely, not hidden): JD entry
 * (/generate/job) -> skill gaps (/generate/skill-gap/:jdId) -> output type
 * (/generate/output/:jdId) -> processing (/generate/processing/:jdId) -> result. Output type is
 * chosen *after* skill gaps now, not before, so bare /generate (which used to be the output-type
 * chooser) just redirects straight into the JD step — there is nothing left for it to show on
 * its own. A stale bookmark to the old /generate/review/:jdId is caught by the catch-all below
 * rather than 404ing or (worse) rendering nothing.
 */
export const router = createBrowserRouter([
  {
    path: '/',
    lazy: async () => {
      const { LandingPage } = await import('../features/landing/LandingPage');
      return { Component: LandingPage };
    },
  },
  {
    path: '/register',
    lazy: async () => {
      const { RegisterPage } = await import('../features/auth/RegisterPage');
      return { Component: RegisterPage };
    },
  },
  {
    path: '/login',
    lazy: async () => {
      const { LoginPage } = await import('../features/auth/LoginPage');
      return { Component: LoginPage };
    },
  },
  {
    lazy: async () => {
      const { ProtectedRoute } = await import('../components/layout/ProtectedRoute');
      return { Component: ProtectedRoute };
    },
    children: [
      {
        path: '/onboarding',
        lazy: async () => {
          const { OnboardingPage } = await import('../features/onboarding/OnboardingPage');
          return { Component: OnboardingPage };
        },
      },
      {
        path: '/profile',
        lazy: async () => {
          const { ProfilePage } = await import('../features/profile/ProfilePage');
          return { Component: ProfilePage };
        },
      },
      {
        path: '/profile/templates',
        lazy: async () => {
          const { MyTemplatesPage } = await import('../features/profile/MyTemplatesPage');
          return { Component: MyTemplatesPage };
        },
      },
      {
        path: '/dashboard',
        lazy: async () => {
          const { DashboardPage } = await import('../features/dashboard/DashboardPage');
          return { Component: DashboardPage };
        },
      },
      {
        path: '/applications',
        lazy: async () => {
          const { ApplicationsPage } = await import('../features/applications/ApplicationsPage');
          return { Component: ApplicationsPage };
        },
      },
      {
        path: '/emails',
        lazy: async () => {
          const { EmailsPage } = await import('../features/emails/EmailsPage');
          return { Component: EmailsPage };
        },
      },
      {
        path: '/analytics',
        lazy: async () => {
          const { AnalyticsPage } = await import('../features/analytics/AnalyticsPage');
          return { Component: AnalyticsPage };
        },
      },
      {
        path: '/generate',
        element: <Navigate to="/generate/job" replace />,
      },
      {
        path: '/generate/job',
        lazy: async () => {
          const { GenerationJobDescriptionPage } = await import('../features/generate/GenerationJobDescriptionPage');
          return { Component: GenerationJobDescriptionPage };
        },
      },
      {
        path: '/generate/skill-gap/:jdId',
        lazy: async () => {
          const { GenerationSkillGapPage } = await import('../features/generate/GenerationSkillGapPage');
          return { Component: GenerationSkillGapPage };
        },
      },
      {
        path: '/generate/output/:jdId',
        lazy: async () => {
          const { OutputTypePage } = await import('../features/generate/OutputTypePage');
          return { Component: OutputTypePage };
        },
      },
      {
        // Obsolete Confirm/Review URL (removed entirely) — a stale bookmark or a back-button
        // press from before this change lands on the step that now actually exists for this JD,
        // rather than 404ing or rendering nothing. A loader redirect, not a rendered component:
        // the deleted Review page must never mount, even for a single tick.
        path: '/generate/review/:jdId',
        loader: ({ params }) => redirect(`/generate/skill-gap/${params.jdId}`),
      },
      {
        path: '/generate/processing/:jdId',
        lazy: async () => {
          const { ProcessingPage } = await import('../features/generate/ProcessingPage');
          return { Component: ProcessingPage };
        },
      },
      {
        path: '/results/optimization/:jdId',
        lazy: async () => {
          const { OptimizationResultPage } = await import('../features/results/OptimizationResultPage');
          return { Component: OptimizationResultPage };
        },
      },
      {
        path: '/results/email/:applicationId',
        lazy: async () => {
          const { EmailResultPage } = await import('../features/results/EmailResultPage');
          return { Component: EmailResultPage };
        },
      },
      {
        path: '/applications/:applicationId',
        lazy: async () => {
          const { ApplicationDetailPage } = await import('../features/results/ApplicationDetailPage');
          return { Component: ApplicationDetailPage };
        },
      },
    ],
  },
]);
