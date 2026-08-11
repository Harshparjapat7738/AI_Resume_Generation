import { SearchIcon } from '../icons';

/** Plain controlled search box — filtering happens client-side over whatever the page already
 *  fetched (see each page's own comment on why: none of the list endpoints support a `q`
 *  param), so there's no debounce/request-cancellation machinery to build here, just a normal
 *  input. */
export function SearchInput({
  value,
  onChange,
  placeholder,
}: {
  value: string;
  onChange: (value: string) => void;
  placeholder: string;
}) {
  return (
    <div className="relative w-full max-w-sm">
      <SearchIcon className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-faint" />
      <input
        type="search"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        aria-label={placeholder}
        className="w-full rounded-xl border border-border bg-surface py-2.5 pr-4 pl-10 text-sm text-ink placeholder:text-ink-faint transition-colors focus:outline-none focus:ring-2 focus:ring-ember-soft/40"
      />
    </div>
  );
}
