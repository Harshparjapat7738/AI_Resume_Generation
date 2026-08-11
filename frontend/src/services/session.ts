/**
 * Session bootstrap. The access token lives in memory only (apiClient.ts) and is lost on
 * page refresh, so every fresh load silently exchanges the HttpOnly refresh cookie for a
 * new one before rendering anything that needs auth — this is what makes a refreshed
 * /results/:id page keep working without sending the user back to /login.
 */
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { queryClient } from '@/app/queryClient';
import { setAccessToken, setHardAuthFailureHandler } from './apiClient';
import { me, refresh, type MeResponse } from './authApi';

export const SESSION_QUERY_KEY = ['session'] as const;

// apiClient.ts has no react-query dependency of its own — it just calls this once it has
// confirmed the refresh cookie itself is dead (not merely a stale access token), so the
// cached session actually reflects that. Every currently-mounted ProtectedRoute reads this
// same query and already redirects to /login the moment `user` becomes null; this is what
// makes that redirect happen without polling, without a full reload, and only when
// re-authentication is genuinely required — a mid-flow access-token expiry alone never
// reaches this, apiClient.ts silently refreshes and retries that case instead.
setHardAuthFailureHandler(() => {
  queryClient.setQueryData(SESSION_QUERY_KEY, null);
});

export async function bootstrapSession(): Promise<MeResponse | null> {
  try {
    const tokens = await refresh();
    setAccessToken(tokens.accessToken);
    return await me();
  } catch {
    setAccessToken(null);
    return null;
  }
}

export function useSession() {
  return useQuery({
    queryKey: SESSION_QUERY_KEY,
    queryFn: bootstrapSession,
    staleTime: Infinity,
    retry: false,
    refetchOnWindowFocus: false,
  });
}

export function useSessionActions() {
  const queryClient = useQueryClient();

  const setSession = (user: MeResponse) => {
    queryClient.setQueryData(SESSION_QUERY_KEY, user);
  };

  const clearSession = () => {
    setAccessToken(null);
    queryClient.setQueryData(SESSION_QUERY_KEY, null);
  };

  return { setSession, clearSession };
}
