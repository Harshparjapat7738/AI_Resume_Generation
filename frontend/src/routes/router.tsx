import { createBrowserRouter } from 'react-router-dom';

/**
 * Route table for the wizard described in the blueprint (§20).
 * Screens are added milestone by milestone; only the landing placeholder exists today.
 */
export const router = createBrowserRouter([
  {
    path: '/',
    lazy: async () => {
      const { LandingPage } = await import('../features/landing/LandingPage');
      return { Component: LandingPage };
    },
  },
]);
