/**
 * Small icon set for the profile page's empty states — one per repeatable section, same
 * hand-drawn stroke style as the rest of the app (landing/components/icons.tsx,
 * components/layout/icons.tsx). Kept local to this feature rather than pulled from either of
 * those so the profile page doesn't reach across feature folders for five glyphs.
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

export function GraduationCapIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <path d="M12 4.5 2.5 9l9.5 4.5L21.5 9 12 4.5Z" />
      <path d="M6.5 11.25v4.1c0 1.5 2.46 2.9 5.5 2.9s5.5-1.4 5.5-2.9v-4.1" />
      <path d="M21.5 9v6" />
    </svg>
  );
}

export function BriefcaseIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <rect x="3" y="7.5" width="18" height="12" rx="2" />
      <path d="M8.25 7.5V6a2 2 0 0 1 2-2h3.5a2 2 0 0 1 2 2v1.5" />
      <path d="M3 12.75c2.7 1.3 5.85 2 9 2s6.3-.7 9-2" />
    </svg>
  );
}

export function SparkleIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <path d="M12 3.5c.55 3.1 1.65 4.7 4.75 5.25-3.1.55-4.2 2.15-4.75 5.25-.55-3.1-1.65-4.7-4.75-5.25 3.1-.55 4.2-2.15 4.75-5.25Z" />
      <path d="M18.5 14.5c.28 1.55.83 2.35 2.38 2.63-1.55.27-2.1 1.07-2.38 2.62-.27-1.55-.82-2.35-2.37-2.62 1.55-.28 2.1-1.08 2.37-2.63Z" />
    </svg>
  );
}

export function FolderIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <path d="M3.5 6.5A1.5 1.5 0 0 1 5 5h4l2 2.5h8a1.5 1.5 0 0 1 1.5 1.5v8a1.5 1.5 0 0 1-1.5 1.5H5A1.5 1.5 0 0 1 3.5 17V6.5Z" />
    </svg>
  );
}

export function AwardIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <circle cx="12" cy="9" r="5.5" />
      <path d="M9 13.75 7.5 20l4.5-2.5 4.5 2.5-1.5-6.25" />
    </svg>
  );
}

export function TrophyIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <path d="M7 4.5h10v5a5 5 0 0 1-10 0v-5Z" />
      <path d="M7 6H4.5a1 1 0 0 0-1 1v.5a3.5 3.5 0 0 0 3.5 3.5M17 6h2.5a1 1 0 0 1 1 1v.5a3.5 3.5 0 0 1-3.5 3.5" />
      <path d="M12 14.5v3M8.5 20.5h7M9.5 17.5h5v3h-5v-3Z" />
    </svg>
  );
}

export function UserCircleIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <circle cx="12" cy="12" r="9" />
      <circle cx="12" cy="9.75" r="3" />
      <path d="M5.8 18.2c1.2-2.4 3.6-3.7 6.2-3.7s5 1.3 6.2 3.7" />
    </svg>
  );
}
