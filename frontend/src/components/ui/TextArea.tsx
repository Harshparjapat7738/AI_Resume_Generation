import { forwardRef } from 'react';
import type { TextareaHTMLAttributes } from 'react';

interface TextAreaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label: string;
  error?: string | undefined;
  hint?: string;
}

export const TextArea = forwardRef<HTMLTextAreaElement, TextAreaProps>(function TextArea(
  { label, error, hint, id, className, ...rest },
  ref,
) {
  const fieldId = id ?? rest.name;
  return (
    <label className="block" htmlFor={fieldId}>
      <span className="text-sm font-medium text-ink">{label}</span>
      <textarea
        ref={ref}
        id={fieldId}
        className={`mt-2 w-full rounded-xl border bg-surface px-4 py-3 text-sm leading-relaxed text-ink placeholder:text-ink-faint focus:outline-none focus:ring-2 focus:ring-ember-soft/40 ${
          error ? 'border-rose' : 'border-border'
        } ${className ?? ''}`}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? `${fieldId}-error` : undefined}
        {...rest}
      />
      {error ? (
        <span id={`${fieldId}-error`} className="mt-1.5 block text-xs text-rose">
          {error}
        </span>
      ) : hint ? (
        <span className="mt-1.5 block text-xs text-ink-faint">{hint}</span>
      ) : null}
    </label>
  );
});
