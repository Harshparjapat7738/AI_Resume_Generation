import { useEffect, useId, useRef, useState } from 'react';

/**
 * Every date-ish field in the profile forms (education/experience/project start-end,
 * certification issued/expiry, achievement date) is stored and sent to profile-service as
 * a plain "YYYY-MM" string — see docs/API_CATALOG.md's profile DTOs. No day-of-month is
 * ever collected or persisted anywhere in this product, so this is a month+year picker,
 * not a full calendar — matching the actual data contract instead of over-collecting.
 *
 * Built from scratch rather than `<input type="month">`: Firefox and desktop Safari don't
 * render a native month picker at all (it silently degrades to a plain text box there), and
 * the ones that do are OS-chrome light-themed popups that would clash hard with this app's
 * dark theme. No date-picker dependency exists in this project yet, and one field type
 * doesn't justify adding one — so this is a small, self-contained popover, styled like every
 * other form control here (see TextField/Select).
 */

const MONTHS = [
  'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
  'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec',
];
const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];

function parse(value: string | undefined): { year: number; month: number } | null {
  if (!value) return null;
  const match = /^(\d{4})-(\d{2})$/.exec(value);
  if (!match) return null;
  return { year: Number(match[1]), month: Number(match[2]) - 1 };
}

function format(year: number, month: number): string {
  return `${year}`.padStart(4, '0') + '-' + `${month + 1}`.padStart(2, '0');
}

function displayLabel(value: string | undefined): string | null {
  const parsed = parse(value);
  if (!parsed) return null;
  return `${MONTH_NAMES[parsed.month]} ${parsed.year}`;
}

interface MonthFieldProps {
  label: string;
  value: string | undefined;
  onChange: (value: string) => void;
  error?: string | undefined;
  placeholder?: string;
  disabled?: boolean;
  /** Inclusive bounds as "YYYY-MM" — e.g. an End field passing the current Start value so a
   *  reversed range can't be picked in the first place, rather than only caught on submit. */
  min?: string | undefined;
  max?: string | undefined;
  /** id of the field this one is paired with, purely for aria-describedby-style hinting. */
  hint?: string;
}

export function MonthField({ label, value, onChange, error, placeholder = 'Select month', disabled, min, max, hint }: MonthFieldProps) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const fieldId = useId();
  const selected = parse(value);
  const minParsed = parse(min);
  const maxParsed = parse(max);

  // The year the grid is currently showing — starts on the selected year, or this year.
  const [viewYear, setViewYear] = useState(() => selected?.year ?? new Date().getFullYear());

  useEffect(() => {
    if (open) setViewYear(selected?.year ?? new Date().getFullYear());
  }, [open]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!open) return;
    const onPointerDown = (event: PointerEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) setOpen(false);
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false);
    };
    document.addEventListener('pointerdown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  const isOutOfRange = (year: number, month: number) => {
    if (minParsed && (year < minParsed.year || (year === minParsed.year && month < minParsed.month))) return true;
    if (maxParsed && (year > maxParsed.year || (year === maxParsed.year && month > maxParsed.month))) return true;
    return false;
  };

  const pick = (month: number) => {
    if (isOutOfRange(viewYear, month)) return;
    onChange(format(viewYear, month));
    setOpen(false);
  };

  return (
    <div ref={rootRef} className="relative">
      <label className="block" htmlFor={fieldId}>
        <span className="text-sm font-medium text-ink">{label}</span>
        <button
          type="button"
          id={fieldId}
          disabled={disabled}
          onClick={() => setOpen((v) => !v)}
          aria-haspopup="dialog"
          aria-expanded={open}
          aria-invalid={Boolean(error)}
          aria-describedby={error ? `${fieldId}-error` : hint ? `${fieldId}-hint` : undefined}
          className={`mt-2 flex w-full items-center justify-between rounded-xl border bg-surface px-4 py-2.5 text-left text-sm transition-colors focus:outline-none focus:ring-2 focus:ring-ember-soft/40 disabled:cursor-not-allowed disabled:opacity-50 ${
            error ? 'border-rose' : 'border-border'
          } ${displayLabel(value) ? 'text-ink' : 'text-ink-faint'}`}
        >
          <span>{displayLabel(value) ?? placeholder}</span>
          <svg viewBox="0 0 20 20" fill="none" aria-hidden="true" className="h-4 w-4 shrink-0 text-ink-faint">
            <rect x="3" y="4.5" width="14" height="12" rx="1.5" stroke="currentColor" strokeWidth="1.5" />
            <path d="M3 8h14M6.5 3v3M13.5 3v3" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
          </svg>
        </button>
      </label>
      {error && (
        <span id={`${fieldId}-error`} className="mt-1.5 block text-xs text-rose">
          {error}
        </span>
      )}
      {!error && hint && (
        <span id={`${fieldId}-hint`} className="mt-1.5 block text-xs text-ink-faint">
          {hint}
        </span>
      )}

      {open && (
        <div
          role="dialog"
          aria-label={`${label} — select month and year`}
          className="absolute z-50 mt-2 w-64 rounded-xl border border-border bg-surface p-3 shadow-xl shadow-black/40"
        >
          <div className="flex items-center justify-between">
            <button
              type="button"
              onClick={() => setViewYear((y) => y - 1)}
              aria-label="Previous year"
              className="flex h-7 w-7 items-center justify-center rounded-lg text-ink-muted transition-colors hover:bg-surface-2 hover:text-ink"
            >
              ‹
            </button>
            <span className="text-sm font-medium text-ink">{viewYear}</span>
            <button
              type="button"
              onClick={() => setViewYear((y) => y + 1)}
              aria-label="Next year"
              className="flex h-7 w-7 items-center justify-center rounded-lg text-ink-muted transition-colors hover:bg-surface-2 hover:text-ink"
            >
              ›
            </button>
          </div>
          <div className="mt-2 grid grid-cols-3 gap-1.5">
            {MONTHS.map((label, month) => {
              const isSelected = selected?.year === viewYear && selected.month === month;
              const outOfRange = isOutOfRange(viewYear, month);
              return (
                <button
                  key={label}
                  type="button"
                  disabled={outOfRange}
                  onClick={() => pick(month)}
                  aria-current={isSelected ? 'date' : undefined}
                  className={`rounded-lg px-2 py-1.5 text-sm transition-colors disabled:cursor-not-allowed disabled:text-ink-faint/40 ${
                    isSelected
                      ? 'bg-linear-to-r from-ember-soft to-rose font-medium text-void'
                      : 'text-ink-muted hover:bg-surface-2 hover:text-ink'
                  }`}
                >
                  {label}
                </button>
              );
            })}
          </div>
          {value && (
            <button
              type="button"
              onClick={() => {
                onChange('');
                setOpen(false);
              }}
              className="mt-2 w-full rounded-lg px-2 py-1.5 text-center text-xs text-ink-faint transition-colors hover:bg-surface-2 hover:text-ink"
            >
              Clear
            </button>
          )}
        </div>
      )}
    </div>
  );
}
