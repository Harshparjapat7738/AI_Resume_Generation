/**
 * Small hand-drawn icon set, one consistent 24x24 stroke style, so the landing
 * page doesn't pull in an icon library dependency for a dozen glyphs.
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

export function UserIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <circle cx="12" cy="8.25" r="3.5" />
      <path d="M4.75 19.5c0-3.87 3.25-7 7.25-7s7.25 3.13 7.25 7" />
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

export function SparkleIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <path d="M12 3.5c.55 3.1 1.65 4.7 4.75 5.25-3.1.55-4.2 2.15-4.75 5.25-.55-3.1-1.65-4.7-4.75-5.25 3.1-.55 4.2-2.15 4.75-5.25Z" />
      <path d="M18.5 14.5c.28 1.55.83 2.35 2.38 2.63-1.55.27-2.1 1.07-2.38 2.62-.27-1.55-.82-2.35-2.37-2.62 1.55-.28 2.1-1.08 2.37-2.63Z" />
    </svg>
  );
}

export function GaugeIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <path d="M4.5 15.5a7.5 7.5 0 1 1 15 0" />
      <path d="M12 15.5 15.25 10" />
      <path d="M4.5 15.5h15" />
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

export function ShieldIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <path d="M12 3.25 5 5.75v5.4c0 4.55 2.95 7.9 7 9.6 4.05-1.7 7-5.05 7-9.6v-5.4L12 3.25Z" />
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

export function MailIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <rect x="3.5" y="5.5" width="17" height="13" rx="1.5" />
      <path d="M4 6.5l8 6.5 8-6.5" />
    </svg>
  );
}

export function EyeOffIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <path d="M3.5 3.5l17 17" />
      <path d="M10.6 5.2c.45-.08.92-.12 1.4-.12 5 0 8.5 4.4 8.5 6.9 0 .78-.35 1.86-1 3M6.1 6.6C4 8.1 2.5 10.2 2.5 12c0 2.5 3.5 6.9 8.5 6.9 1.4 0 2.7-.35 3.85-.95" />
      <path d="M9.4 9.4a3 3 0 0 0 4.2 4.2" />
    </svg>
  );
}

export function ArrowRightIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <path d="M4.5 12h15" />
      <path d="M13.5 6l6 6-6 6" />
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

export function CheckIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <path d="M4.5 12.5l5 5 10-11" />
    </svg>
  );
}

export function DatabaseIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <ellipse cx="12" cy="6" rx="7" ry="2.5" />
      <path d="M5 6v6c0 1.38 3.13 2.5 7 2.5s7-1.12 7-2.5V6" />
      <path d="M5 12v6c0 1.38 3.13 2.5 7 2.5s7-1.12 7-2.5v-6" />
    </svg>
  );
}

export function PlayIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <circle cx="12" cy="12" r="9" />
      <path d="M10 8.5l6 3.5-6 3.5v-7Z" fill="currentColor" stroke="none" />
    </svg>
  );
}

export function UploadIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <path d="M12 15.5V4.5M8 8.5l4-4 4 4" />
      <path d="M4.75 15.5v3a1.5 1.5 0 0 0 1.5 1.5h11.5a1.5 1.5 0 0 0 1.5-1.5v-3" />
    </svg>
  );
}

export function SearchIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <circle cx="10.75" cy="10.75" r="6.25" />
      <path d="M19.25 19.25l-4.3-4.3" />
    </svg>
  );
}

export function ChecklistIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <path d="M4.75 6.5l1.5 1.5 2.5-2.5M4.75 13l1.5 1.5 2.5-2.5M4.75 19.5l1.5 1.5 2.5-2.5" />
      <path d="M12 6.5h7.25M12 13h7.25M12 19.5h7.25" />
    </svg>
  );
}

export function TrophyIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <path d="M7 4.75h10v4.5a5 5 0 0 1-10 0v-4.5Z" />
      <path d="M7 6h-1.75a2 2 0 0 0 0 4H7M17 6h1.75a2 2 0 0 1 0 4H17" />
      <path d="M12 14.25v3M9 20.25h6M9.75 20.25v-1.5a1 1 0 0 1 1-1h2.5a1 1 0 0 1 1 1v1.5" />
    </svg>
  );
}

export function QuoteIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className} fill="currentColor" stroke="none">
      <path d="M9.5 6.5c-3 1-4.5 3.4-4.5 6.3 0 2.6 1.7 4.4 3.9 4.4a3.3 3.3 0 0 0 3.4-3.3c0-1.7-1.1-2.9-2.7-3.1.2-1.4 1.2-2.6 2.7-3.2L9.5 6.5Zm9 0c-3 1-4.5 3.4-4.5 6.3 0 2.6 1.7 4.4 3.9 4.4a3.3 3.3 0 0 0 3.4-3.3c0-1.7-1.1-2.9-2.7-3.1.2-1.4 1.2-2.6 2.7-3.2l-2.8-1.1Z" />
    </svg>
  );
}

export function LayersIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <path d="M12 3.75 3.75 8.5 12 13.25 20.25 8.5 12 3.75Z" />
      <path d="M3.75 12.5 12 17.25l8.25-4.75M3.75 16.5 12 21.25l8.25-4.75" />
    </svg>
  );
}

export function TargetIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <circle cx="12" cy="12" r="8" />
      <circle cx="12" cy="12" r="4.25" />
      <circle cx="12" cy="12" r="0.75" fill="currentColor" stroke="none" />
    </svg>
  );
}

export function TrendUpIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <path d="M4.5 16.5 10 11l3.5 3.5 6-6.5" />
      <path d="M15.75 8h3.75v3.75" />
    </svg>
  );
}
