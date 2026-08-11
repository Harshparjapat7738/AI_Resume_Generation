import type { ProfileCompletion } from '@/services/profileApi';
import { PROFILE_SECTIONS } from '../sections';

type ItemState = 'current' | 'complete' | 'incomplete';

function stateOf(isActive: boolean, isComplete: boolean): ItemState {
  if (isActive) return 'current';
  return isComplete ? 'complete' : 'incomplete';
}

/** Numbered badge: a filled gradient circle with the step number for the active section, a
 *  mint check for a completed one, a plain outlined number otherwise — same three states in
 *  both the desktop rail and the mobile strip below. */
function SectionBadge({ state, index }: { state: ItemState; index: number }) {
  if (state === 'complete') {
    return (
      <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-mint/15 text-mint" aria-hidden="true">
        <svg viewBox="0 0 20 20" fill="currentColor" className="h-3.5 w-3.5">
          <path
            fillRule="evenodd"
            d="M16.7 5.3a1 1 0 0 1 0 1.4l-7.5 7.5a1 1 0 0 1-1.4 0l-3.5-3.5a1 1 0 1 1 1.4-1.4l2.8 2.8 6.8-6.8a1 1 0 0 1 1.4 0Z"
            clipRule="evenodd"
          />
        </svg>
      </span>
    );
  }
  if (state === 'current') {
    return (
      <span
        className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-linear-to-br from-ember-soft to-rose text-[11px] font-semibold text-void"
        aria-hidden="true"
      >
        {index + 1}
      </span>
    );
  }
  return (
    <span
      className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full border border-border-strong text-[11px] font-semibold text-ink-faint"
      aria-hidden="true"
    >
      {index + 1}
    </span>
  );
}

/** Section jump-list — desktop sticky rail and mobile horizontal strip, same data, same
 *  click behaviour. Selecting a section swaps which one is mounted in the main content area
 *  (ProfilePage.tsx); this never scrolls anywhere, since only the active section ever renders. */
export function ProfileSidebar({
  sections,
  activeIndex,
  onSelect,
}: {
  sections: ProfileCompletion['sections'];
  activeIndex: number;
  onSelect: (index: number) => void;
}) {
  return (
    <div>
      {/* Desktop: sticky vertical rail */}
      <nav aria-label="Profile sections" className="hidden lg:block">
        <div className="sticky top-8">
          <ul className="space-y-1">
            {PROFILE_SECTIONS.map((section, index) => {
              const isActive = index === activeIndex;
              const state = stateOf(isActive, sections[section.key]);
              return (
                <li key={section.key}>
                  <button
                    type="button"
                    onClick={() => onSelect(index)}
                    aria-current={isActive ? 'page' : undefined}
                    className={`flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left text-sm transition-colors ${
                      isActive ? 'bg-surface-2 font-medium text-ink' : 'text-ink-muted hover:bg-surface-2/60 hover:text-ink'
                    }`}
                  >
                    <SectionBadge state={state} index={index} />
                    {section.label}
                  </button>
                </li>
              );
            })}
          </ul>
        </div>
      </nav>

      {/* Mobile / tablet: horizontal scroller, sticky under the page header */}
      <nav
        aria-label="Profile sections"
        className="sticky top-0 z-10 -mx-4 border-b border-border bg-void/95 px-4 py-3 backdrop-blur sm:-mx-6 sm:px-6 lg:hidden"
      >
        <ul className="flex gap-2 overflow-x-auto">
          {PROFILE_SECTIONS.map((section, index) => {
            const isActive = index === activeIndex;
            const state = stateOf(isActive, sections[section.key]);
            return (
              <li key={section.key} className="shrink-0">
                <button
                  type="button"
                  onClick={() => onSelect(index)}
                  aria-current={isActive ? 'page' : undefined}
                  className={`flex items-center gap-2 rounded-full border py-1.5 pl-1.5 pr-3 text-xs font-medium whitespace-nowrap transition-colors ${
                    isActive ? 'border-ember-soft text-ink' : 'border-border-strong text-ink-muted hover:text-ink'
                  }`}
                >
                  <SectionBadge state={state} index={index} />
                  {section.label}
                </button>
              </li>
            );
          })}
        </ul>
      </nav>
    </div>
  );
}
