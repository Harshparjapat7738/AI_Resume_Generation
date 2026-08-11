/**
 * Icon set for the dashboard's sidebar, summary cards and widgets — same hand-drawn stroke
 * style as every other feature-local icon file in this app (see e.g.
 * features/profile/components/icons.tsx, components/layout/icons.tsx).
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

export function HomeIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <path d="M4 11.5 12 4l8 7.5" />
      <path d="M6 10v8.5a1 1 0 0 0 1 1h3.5v-5h3v5H17a1 1 0 0 0 1-1V10" />
    </svg>
  );
}

export function LayersIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <path d="m12 3.5 8 4.25L12 12 4 7.75 12 3.5Z" />
      <path d="m4 12 8 4.25L20 12" />
      <path d="m4 16.25 8 4.25 8-4.25" />
    </svg>
  );
}

export function DocumentIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <path d="M6.5 3.75h7l4 4v11.5a1 1 0 0 1-1 1h-10a1 1 0 0 1-1-1V4.75a1 1 0 0 1 1-1Z" />
      <path d="M13.25 3.75v4h4" />
      <path d="M8.75 12.5h6.5M8.75 15.75h6.5M8.75 9.25h2.5" />
    </svg>
  );
}

export function SendIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <path d="M20.5 3.5 3 10.2l6.7 2.6m10.8-9.3L14.3 21l-4.6-8.2m10.8-9.3L9.7 12.8" />
    </svg>
  );
}

export function MailIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <rect x="3.5" y="5.5" width="17" height="13" rx="1.5" />
      <path d="M4 6.5l8 6.5 8-6.5" />
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

export function BarChartIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <path d="M5 20V10M12 20V4M19 20v-7" />
      <path d="M3.5 20.5h17" />
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

export function GearIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <circle cx="12" cy="12" r="3" />
      <path d="M12 3.5v2.2M12 18.3v2.2M20.5 12h-2.2M5.7 12H3.5M17.7 6.3l-1.55 1.55M7.85 16.15 6.3 17.7M17.7 17.7l-1.55-1.55M7.85 7.85 6.3 6.3" />
    </svg>
  );
}

export function BellIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <path d="M6 10.5a6 6 0 0 1 12 0c0 3.6 1 5 1.5 5.75H4.5C5 15.5 6 14.1 6 10.5Z" />
      <path d="M10 19a2 2 0 0 0 4 0" />
    </svg>
  );
}

export function StarIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className} fill="currentColor" stroke="none">
      <path d="M12 3.5 14.5 9l6 .75-4.4 4.15L17.3 20 12 16.9 6.7 20l1.2-6.1L3.5 9.75 9.5 9 12 3.5Z" />
    </svg>
  );
}

export function PlusCircleIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 8.25v7.5M8.25 12h7.5" />
    </svg>
  );
}

export function ChevronRightIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <path d="m9 5.5 7 6.5-7 6.5" />
    </svg>
  );
}

export function MenuIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <path d="M4 6.5h16M4 12h16M4 17.5h16" />
    </svg>
  );
}

export function XIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <path d="M6 6l12 12M18 6 6 18" />
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

export function ShieldCheckIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <path d="M12 3.25 5 5.75v5.4c0 4.55 2.95 7.9 7 9.6 4.05-1.7 7-5.05 7-9.6v-5.4L12 3.25Z" />
      <path d="M9.25 12.25l1.9 1.9 3.6-4" />
    </svg>
  );
}

export function LockIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <rect x="5.25" y="10.5" width="13.5" height="9.5" rx="1.5" />
      <path d="M8 10.5V7.75a4 4 0 0 1 8 0V10.5" />
    </svg>
  );
}

export function BadgeCheckIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <path d="m12 3 2.2 1.4 2.6-.2 1 2.4 2.2 1.4-.5 2.6.5 2.6-2.2 1.4-1 2.4-2.6-.2L12 18l-2.2-1.4-2.6.2-1-2.4-2.2-1.4.5-2.6-.5-2.6 2.2-1.4 1-2.4 2.6.2L12 3Z" />
      <path d="m9 12 2 2 4-4" />
    </svg>
  );
}

export function SearchIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <circle cx="10.5" cy="10.5" r="6.5" />
      <path d="m20 20-4.6-4.6" />
    </svg>
  );
}

export function ChevronLeftIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <path d="m15 5.5-7 6.5 7 6.5" />
    </svg>
  );
}

export function DownloadIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <path d="M12 3.5v11.5M8 11.5l4 4 4-4" />
      <path d="M5 17.5v2a1.5 1.5 0 0 0 1.5 1.5h11a1.5 1.5 0 0 0 1.5-1.5v-2" />
    </svg>
  );
}

export function CopyIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <rect x="8.5" y="8.5" width="11" height="11" rx="1.5" />
      <path d="M15.5 8.5V6a1.5 1.5 0 0 0-1.5-1.5H6A1.5 1.5 0 0 0 4.5 6v8A1.5 1.5 0 0 0 6 15.5h2.5" />
    </svg>
  );
}

export function TrashIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <path d="M5 7.5h14M9.5 7.5V5.75a1.25 1.25 0 0 1 1.25-1.25h2.5a1.25 1.25 0 0 1 1.25 1.25V7.5" />
      <path d="M7 7.5 7.7 19a1.5 1.5 0 0 0 1.5 1.4h5.6a1.5 1.5 0 0 0 1.5-1.4l.7-11.5" />
    </svg>
  );
}

export function EyeIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <path d="M3 12s3.5-6.5 9-6.5 9 6.5 9 6.5-3.5 6.5-9 6.5-9-6.5-9-6.5Z" />
      <circle cx="12" cy="12" r="2.5" />
    </svg>
  );
}

export function ArrowUpDownIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <path d="M7 4v16M7 4 3.5 7.5M7 4l3.5 3.5" />
      <path d="M17 20V4M17 20l3.5-3.5M17 20l-3.5-3.5" />
    </svg>
  );
}

export function AlertTriangleIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <path d="M12 4 3 19.5h18L12 4Z" />
      <path d="M12 10.25v4M12 17h.01" />
    </svg>
  );
}
