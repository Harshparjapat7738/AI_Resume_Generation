import { useEffect, useRef, useState } from 'react';

type Status = 'idle' | 'saved';

/** Flips to "saved" for a few seconds after `pulse()` is called, then back to idle.
 *  Local UI feedback only — the save itself already happened by the time this fires. */
export function useSaveStatus() {
  const [status, setStatus] = useState<Status>('idle');
  const timeoutRef = useRef<ReturnType<typeof setTimeout>>(undefined);

  useEffect(() => () => clearTimeout(timeoutRef.current), []);

  const pulse = () => {
    setStatus('saved');
    clearTimeout(timeoutRef.current);
    timeoutRef.current = setTimeout(() => setStatus('idle'), 2500);
  };

  return { status, pulse };
}

/** Small inline "✓ Saved" indicator. Renders nothing while idle so it never adds layout
 *  noise to a form the user hasn't touched yet. */
export function SaveStatus({ status }: { status: Status }) {
  if (status === 'idle') return null;
  return (
    <span role="status" className="inline-flex items-center gap-1 text-xs font-medium text-mint">
      <svg viewBox="0 0 20 20" fill="currentColor" className="h-3.5 w-3.5" aria-hidden="true">
        <path
          fillRule="evenodd"
          d="M16.7 5.3a1 1 0 0 1 0 1.4l-7.5 7.5a1 1 0 0 1-1.4 0l-3.5-3.5a1 1 0 1 1 1.4-1.4l2.8 2.8 6.8-6.8a1 1 0 0 1 1.4 0Z"
          clipRule="evenodd"
        />
      </svg>
      Changes saved
    </span>
  );
}
