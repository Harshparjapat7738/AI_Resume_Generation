import { ApiError } from '@/services/apiClient';

/**
 * Every backend failure already carries a safe, human-readable {@code message} (never a
 * stack trace or exception class name — platform-common's GlobalExceptionHandler
 * guarantees that). This just renders it consistently and adds a fallback for network-level
 * failures the backend never saw.
 */
export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    return error.body.message;
  }
  if (error instanceof Error) {
    return error.message === 'Failed to fetch'
      ? "Can't reach the server. Check your connection and try again."
      : 'Something went wrong. Please try again.';
  }
  return 'Something went wrong. Please try again.';
}

export function ErrorBanner({ error }: { error: unknown }) {
  return (
    <div
      role="alert"
      className="rounded-xl border border-rose/30 bg-rose/10 px-4 py-3 text-sm text-ink"
    >
      {describeError(error)}
    </div>
  );
}
