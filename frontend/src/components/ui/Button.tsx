import type { ButtonHTMLAttributes, ReactNode } from 'react';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'ghost' | 'accent';
  loading?: boolean;
  children: ReactNode;
}

const base =
  'inline-flex items-center justify-center gap-2 rounded-full px-6 py-3 text-sm font-semibold transition-all disabled:cursor-not-allowed disabled:opacity-60';

const variants: Record<NonNullable<ButtonProps['variant']>, string> = {
  primary: 'bg-linear-to-r from-ember-soft to-rose text-void hover:-translate-y-0.5 disabled:hover:translate-y-0',
  secondary: 'border border-border-strong text-ink hover:border-ink-muted',
  ghost: 'text-ink-muted hover:text-ink',
  // Same surface fill as `secondary`, but the border itself is the ember→rose gradient
  // (the classic two-background "padding-box / border-box" trick) instead of a flat
  // border-only vs. instead of a full gradient fill (that's `primary`) — for actions that
  // deserve a bit more visual weight than `secondary` without competing with the page's one
  // primary action.
  // NB: this needs the `background` *shorthand* (arbitrary-property `[background:…]`), not
  // the `bg-[…]` utility — that one only ever emits `background-image`, which doesn't accept
  // the trailing `padding-box`/`border-box` origin/clip keywords and silently drops the
  // whole declaration as invalid CSS.
  accent:
    'border-2 border-transparent text-ink [background:linear-gradient(var(--color-surface),var(--color-surface))_padding-box,linear-gradient(90deg,var(--color-ember-soft),var(--color-rose))_border-box] hover:brightness-110',
};

export function Button({ variant = 'primary', loading = false, className, children, disabled, ...rest }: ButtonProps) {
  return (
    <button
      className={`${base} ${variants[variant]} ${className ?? ''}`}
      disabled={disabled || loading}
      {...rest}
    >
      {loading && (
        <span
          className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-current border-t-transparent"
          aria-hidden="true"
        />
      )}
      {children}
    </button>
  );
}
