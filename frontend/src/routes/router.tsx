import { createBrowserRouter } from 'react-router-dom';

/**
 * Route table for the wizard described in the blueprint (§20).
 *
 * The landing page ('/') stays the central page for both anonymous and authenticated
 * visitors — it is never bypassed after login. /onboarding, /generate/*, /results/:id,
 * /dashboard and /profile require an active session — see ProtectedRoute. Login/register
 * always land on /onboarding (new/incomplete profile) or the originally-intended page
 * (existing, complete profile) — never automatically into a generation workflow. See
 * docs/API_INTEGRATION.md, "Authentication flow".
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
        path: '/resumes',
        lazy: async () => {
          const { ResumesPage } = await import('../features/resumes/ResumesPage');
          return { Component: ResumesPage };
        },
      },
      {
        path: '/cover-letters',
        lazy: async () => {
          const { CoverLettersPage } = await import('../features/cover-letters/CoverLettersPage');
          return { Component: CoverLettersPage };
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
        path: '/templates',
        lazy: async () => {
          const { TemplatesPage } = await import('../features/templates/TemplatesPage');
          return { Component: TemplatesPage };
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
        lazy: async () => {
          const { OutputTypePage } = await import('../features/generate/OutputTypePage');
          return { Component: OutputTypePage };
        },
      },
      {
        path: '/generate/job',
        lazy: async () => {
          const { GenerationJobDescriptionPage } = await import('../features/generate/GenerationJobDescriptionPage');
          return { Component: GenerationJobDescriptionPage };
        },
      },
      {
        path: '/generate/review/:jdId',
        lazy: async () => {
          const { GenerationReviewPage } = await import('../features/generate/GenerationReviewPage');
          return { Component: GenerationReviewPage };
        },
      },
      {
        path: '/generate/template/:jdId',
        lazy: async () => {
          const { TemplatePage } = await import('../features/generate/TemplatePage');
          return { Component: TemplatePage };
        },
      },
      {
        path: '/generate/processing/:jdId',
        lazy: async () => {
          const { ProcessingPage } = await import('../features/generate/ProcessingPage');
          return { Component: ProcessingPage };
        },
      },
      {
        path: '/results/:resumeId',
        lazy: async () => {
          const { ResultPage } = await import('../features/results/ResultPage');
          return { Component: ResultPage };
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
        path: '/results/cover-letter/:applicationId',
        lazy: async () => {
          const { CoverLetterResultPage } = await import('../features/results/CoverLetterResultPage');
          return { Component: CoverLetterResultPage };
        },
      },
      {
        path: '/results/all/:applicationId',
        lazy: async () => {
          const { AllResultPage } = await import('../features/results/AllResultPage');
          return { Component: AllResultPage };
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
