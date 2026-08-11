import { QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from 'react-router-dom';
import { ToastViewport } from '@/components/ui/toast';
import { queryClient } from './queryClient';
import { router } from '../routes/router';

/**
 * Application shell.
 *
 * Server state lives in TanStack Query; Zustand is reserved for client-only UI state
 * (wizard step, panel visibility, theme). Do not mirror server data into Zustand.
 */
export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
      <ToastViewport />
    </QueryClientProvider>
  );
}
