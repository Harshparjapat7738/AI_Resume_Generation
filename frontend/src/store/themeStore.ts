import { create } from 'zustand';

/**
 * Light/dark for the landing page only (see `#top[data-theme]` in index.css) — the
 * authenticated app has no light theme and isn't touched by this. First real use of
 * Zustand in this codebase, for exactly the case App.tsx's own doc comment already
 * reserves it for ("client-only UI state — wizard step, panel visibility, theme").
 */

export type Theme = 'light' | 'dark';

const STORAGE_KEY = 'careerforge-landing-theme';

function systemTheme(): Theme {
  if (typeof window === 'undefined' || !window.matchMedia) return 'light';
  // "Light-first design": an unset/indeterminate system preference falls back to
  // light, matching the brief — only an explicit `prefers-color-scheme: dark` opts
  // a first-time visitor into dark.
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

function readStoredTheme(): Theme | null {
  if (typeof window === 'undefined') return null;
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY);
    return stored === 'light' || stored === 'dark' ? stored : null;
  } catch {
    // Storage can throw in locked-down browser contexts (private mode, disabled
    // storage) — the toggle should still work for the session, just not persist.
    return null;
  }
}

function persistTheme(theme: Theme): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, theme);
  } catch {
    // Same as above — non-fatal, the in-memory store still has the right value.
  }
}

interface ThemeStore {
  theme: Theme;
  setTheme: (theme: Theme) => void;
  toggleTheme: () => void;
}

export const useThemeStore = create<ThemeStore>((set, get) => ({
  theme: readStoredTheme() ?? systemTheme(),
  setTheme: (theme) => {
    persistTheme(theme);
    set({ theme });
  },
  toggleTheme: () => {
    const next: Theme = get().theme === 'dark' ? 'light' : 'dark';
    persistTheme(next);
    set({ theme: next });
  },
}));
