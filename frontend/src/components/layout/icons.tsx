/**
 * Small icon set for the app-wide chrome (headers, menus). Same hand-drawn stroke
 * style as the landing page's icon set (features/landing/components/icons.tsx),
 * kept local here so this shared layout code doesn't reach into a feature folder.
 */
interface IconProps {
  className?: string;
}

const base = {
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.75,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
  'aria-hidden': true,
};

export function SparkleIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <path d="M12 3.5c.55 3.1 1.65 4.7 4.75 5.25-3.1.55-4.2 2.15-4.75 5.25-.55-3.1-1.65-4.7-4.75-5.25 3.1-.55 4.2-2.15 4.75-5.25Z" />
      <path d="M18.5 14.5c.28 1.55.83 2.35 2.38 2.63-1.55.27-2.1 1.07-2.38 2.62-.27-1.55-.82-2.35-2.37-2.62 1.55-.28 2.1-1.08 2.37-2.63Z" />
    </svg>
  );
}

export function ChevronDownIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <path d="M5.5 8.5 12 15l6.5-6.5" />
    </svg>
  );
}

export function LogOutIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <path d="M9 4.5H5.75a1.25 1.25 0 0 0-1.25 1.25v12.5a1.25 1.25 0 0 0 1.25 1.25H9" />
      <path d="M15.5 16l4-4-4-4" />
      <path d="M19.25 12h-11" />
    </svg>
  );
}

export function UserIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <circle cx="12" cy="8.25" r="3.5" />
      <path d="M4.75 19.5c0-3.87 3.25-7 7.25-7s7.25 3.13 7.25 7" />
    </svg>
  );
}

export function GridIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <rect x="4" y="4" width="7" height="7" rx="1.25" />
      <rect x="13" y="4" width="7" height="7" rx="1.25" />
      <rect x="4" y="13" width="7" height="7" rx="1.25" />
      <rect x="13" y="13" width="7" height="7" rx="1.25" />
    </svg>
  );
}
