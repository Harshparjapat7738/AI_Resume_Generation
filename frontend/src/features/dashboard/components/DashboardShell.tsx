import type { ReactNode } from 'react';
import { FullScreenSpinner } from '@/components/ui/FullScreenSpinner';
import type { MeResponse } from '@/services/authApi';
import { useSession } from '@/services/session';
import { useLogout } from '../hooks';
import { DashboardSidebar } from './DashboardSidebar';

export interface DashboardShellContext {
  user: MeResponse;
  displayName: string;
  onLogout: () => void;
}

/** The sidebar + content shell every dashboard-area page shares (Dashboard itself and all six
 *  dedicated data pages) — pulled out of DashboardPage.tsx so that shared chrome lives in one
 *  place instead of being copy-pasted into every new route. Each page still owns its own
 *  header/content via `children`, which receives the resolved (non-null) session and the
 *  shared logout handler so pages never need to re-derive either. */
export function DashboardShell({ children }: { children: (ctx: DashboardShellContext) => ReactNode }) {
  const { data: user } = useSession();
  const handleLogout = useLogout();

  if (!user) {
    return <FullScreenSpinner label="Loading…" />;
  }

  const displayName = user.displayName?.trim() || user.email;

  return (
    <div className="flex min-h-screen bg-void">
      <DashboardSidebar userName={displayName} userEmail={user.email} />
      <div className="min-w-0 flex-1">
        <main className="mx-auto max-w-7xl px-4 py-8 pl-16 sm:px-6 lg:pl-8 lg:py-10">
          {children({ user, displayName, onLogout: handleLogout })}
        </main>
      </div>
    </div>
  );
}
