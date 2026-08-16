import { useThemeStore } from '@/store/themeStore';
import { MoonIcon, SunIcon } from './icons';

/**
 * Sun/moon switch for the app-wide light/dark theme (see `html[data-theme]` in
 * index.css, `useThemeStore`, and `App.tsx`'s `ThemeEffect`) — shared by the landing
 * page's SiteHeader and every authenticated screen's chrome (AppHeader,
 * DashboardSidebar, ReviewHeader), so there is exactly one toggle implementation for
 * the one theme that now applies everywhere. A track-and-thumb control rather than a
 * plain icon button so the current state reads at a glance, not just on hover/focus.
 */
export function ThemeToggle({ className = '' }: { className?: string }) {
  const theme = useThemeStore((state) => state.theme);
  const toggleTheme = useThemeStore((state) => state.toggleTheme);
  const isDark = theme === 'dark';

  return (
    <button
      type="button"
      onClick={toggleTheme}
      role="switch"
      aria-checked={isDark}
      aria-label={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
      title={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
      className={`relative inline-flex h-8 w-14 shrink-0 items-center rounded-full border border-border-strong bg-surface-2 px-1 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember-soft ${className}`}
    >
      <SunIcon
        className={`absolute left-1.5 h-4 w-4 transition-opacity duration-200 ${isDark ? 'opacity-0' : 'opacity-100 text-ember-soft'}`}
      />
      <MoonIcon
        className={`absolute right-1.5 h-4 w-4 transition-opacity duration-200 ${isDark ? 'opacity-100 text-ink-muted' : 'opacity-0'}`}
      />
      <span
        aria-hidden="true"
        className={`flex h-6 w-6 items-center justify-center rounded-full bg-linear-to-br from-ember-soft to-rose shadow-sm transition-transform duration-300 ease-out ${
          isDark ? 'translate-x-6' : 'translate-x-0'
        }`}
      >
        {isDark ? (
          <MoonIcon className="h-3.5 w-3.5 text-void" />
        ) : (
          <SunIcon className="h-3.5 w-3.5 text-void" />
        )}
      </span>
    </button>
  );
}
