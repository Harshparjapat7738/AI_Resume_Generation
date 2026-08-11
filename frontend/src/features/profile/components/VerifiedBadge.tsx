/**
 * Every profile record carries a stable evidenceId that the grounded-generation pipeline
 * cites (see ExperienceManager's doc comment) — real and load-bearing, but not something a
 * user needs to read as a raw string. This is the human-friendly stand-in: the id itself
 * stays attached to the record and is still sent to the backend on every edit/delete, it's
 * just not printed on screen any more.
 */
export function VerifiedBadge() {
  return (
    <span className="inline-flex items-center gap-1 text-[11px] font-medium text-mint">
      <svg viewBox="0 0 20 20" fill="currentColor" className="h-3 w-3" aria-hidden="true">
        <path
          fillRule="evenodd"
          d="M16.7 5.3a1 1 0 0 1 0 1.4l-7.5 7.5a1 1 0 0 1-1.4 0l-3.5-3.5a1 1 0 1 1 1.4-1.4l2.8 2.8 6.8-6.8a1 1 0 0 1 1.4 0Z"
          clipRule="evenodd"
        />
      </svg>
      Verified evidence
    </span>
  );
}
