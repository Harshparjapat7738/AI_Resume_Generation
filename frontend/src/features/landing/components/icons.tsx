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
