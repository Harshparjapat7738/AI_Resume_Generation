import { forwardRef } from 'react';
import type { SelectHTMLAttributes } from 'react';

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: string;
  error?: string | undefined;
}

/** Same visual language as TextField (label, border, focus ring, error state) so a form
 *  mixing text inputs and dropdowns reads as one system. */
export const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select(
  { label, error, id, className, children, ...rest },
  ref,
) {
  const fieldId = id ?? rest.name;
  return (
    <label className="block" htmlFor={fieldId}>
      <span className="text-sm font-medium text-ink">{label}</span>
      <div className="relative mt-2">
        <select
          ref={ref}
          id={fieldId}
          className={`w-full appearance-none rounded-xl border bg-surface px-4 py-2.5 pr-9 text-sm text-ink transition-colors focus:outline-none focus:ring-2 focus:ring-ember-soft/40 ${
            error ? 'border-rose' : 'border-border'
          } ${className ?? ''}`}
          aria-invalid={Boolean(error)}
          aria-describedby={error ? `${fieldId}-error` : undefined}
          {...rest}
        >
          {children}
        </select>
        <svg
          viewBox="0 0 20 20"
          fill="none"
          aria-hidden="true"
          className="pointer-events-none absolute top-1/2 right-3 h-4 w-4 -translate-y-1/2 text-ink-faint"
        >
          <path d="M5 7.5 10 12.5 15 7.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </div>
      {error && (
        <span id={`${fieldId}-error`} className="mt-1.5 block text-xs text-rose">
          {error}
        </span>
      )}
    </label>
  );
});
