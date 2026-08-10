/**
 * Session bootstrap. The access token lives in memory only (apiClient.ts) and is lost on
 * page refresh, so every fresh load silently exchanges the HttpOnly refresh cookie for a
 * new one before rendering anything that needs auth — this is what makes a refreshed
 * /results/:id page keep working without sending the user back to /login.
 */
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { setAccessToken } from './apiClient';
import { me, refresh, type MeResponse } from './authApi';

export const SESSION_QUERY_KEY = ['session'] as const;

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
