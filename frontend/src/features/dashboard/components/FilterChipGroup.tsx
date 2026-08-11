/** The same pill-button filter row Dashboard's applications section already used, generalised
 *  so Applications/Resumes/Cover Letters/Emails pages can each build their own filter sets
 *  (type, status, source, …) without re-styling the buttons every time. */
export function FilterChipGroup<T extends string>({
  label,
  options,
  value,
  onChange,
}: {
  label?: string;
  options: readonly { id: T; label: string }[];
  value: T;
  onChange: (value: T) => void;
}) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      {label && <span className="mr-1 text-xs font-medium text-ink-faint">{label}</span>}
      {options.map((option) => (
        <button
          key={option.id}
          type="button"
          onClick={() => onChange(option.id)}
          aria-pressed={value === option.id}
          className={`rounded-full border px-3 py-1.5 text-xs font-medium transition-colors ${
            value === option.id
              ? 'border-ember-soft text-ember-soft'
              : 'border-border-strong text-ink-muted hover:text-ink'
          }`}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
