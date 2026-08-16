import { useEffect } from 'react';
import { QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from 'react-router-dom';
import { ToastViewport } from '@/components/ui/toast';
import { useThemeStore } from '@/store/themeStore';
import { queryClient } from './queryClient';
import { router } from '../routes/router';

/** Applies the current theme to <html> (see `html[data-theme]` in index.css) and keeps
 *  the browser chrome (mobile address-bar color) in sync — the one place this needs to
 *  happen, now that light/dark applies to every route, not just the landing page. */
function ThemeEffect() {
  const theme = useThemeStore((state) => state.theme);

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
    const meta = document.querySelector('meta[name="theme-color"]');
    meta?.setAttribute('content', theme === 'light' ? '#faf9fd' : '#08080b');
  }, [theme]);

  return null;
}

/**
 * Application shell.
 *
 * Server state lives in TanStack Query; Zustand is reserved for client-only UI state
 * (wizard step, panel visibility, theme). Do not mirror server data into Zustand.
 */
export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ThemeEffect />
      <RouterProvider router={router} />
      <ToastViewport />
    </QueryClientProvider>
  );
}
