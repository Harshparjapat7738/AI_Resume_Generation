import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { FullScreenSpinner } from '@/components/ui/FullScreenSpinner';
import { useSession } from '@/services/session';

/** Redirects to /login (preserving the intended destination) when there is no active session. */
export function ProtectedRoute() {
  const { data: user, isLoading } = useSession();
  const location = useLocation();

  if (isLoading) {
    return <FullScreenSpinner label="Checking your session…" />;
  }
  if (!user) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  return <Outlet />;
}
