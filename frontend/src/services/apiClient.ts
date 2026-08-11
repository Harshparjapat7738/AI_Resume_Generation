/**
 * Thin fetch wrapper for the API Gateway.
 *
 * Every request goes to the gateway; the browser never holds a Groq key, a MongoDB URI
 * or S3 credentials. The refresh token lives in an HttpOnly cookie and is never readable
 * from JavaScript.
 *
 * Access-token expiry: the gateway issues a short-lived access token (900s — see
 * JWT_ACCESS_EXPIRATION) and rejects an expired/invalid one with 401 AUTH_TOKEN_INVALID
 * (JwtAuthenticationFilter.java). Before this file's retry-once logic existed, that 15-minute
 * mark was reachable mid-session (most easily during the profile/onboarding wizard — it's the
 * longest, most interaction-heavy flow in the app) with nothing to renew the token: every
 * request after that point failed with "The access token is invalid or expired" until a full
 * page reload re-ran the one-time bootstrap refresh in session.ts. This file now reacts to
 * that specific failure itself, using the same still-valid HttpOnly refresh cookie
 * session.ts's bootstrap already relies on, and transparently replays the original request —
 * so a normal, mid-flow token expiry is invisible to the caller and never drops entered data.
 */

export interface ApiErrorBody {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
  correlationId?: string;
  fieldErrors?: { field: string; message: string }[];
}

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly body: ApiErrorBody,
  ) {
    super(body.message);
    this.name = 'ApiError';
  }
}

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

let accessToken: string | null = null;

/** Access token is kept in memory only — not in localStorage, which is XSS-readable. */
export function setAccessToken(token: string | null): void {
  accessToken = token;
}

/**
 * Fires once the refresh cookie itself turns out to be gone/invalid too — i.e. the moment
 * re-authentication is genuinely required, not just this one access token. Registered by
 * session.ts (which owns the session query cache) rather than imported here, so this module
 * stays free of any React/query-client dependency — see setHardAuthFailureHandler.
 */
let hardAuthFailureHandler: (() => void) | null = null;

export function setHardAuthFailureHandler(handler: (() => void) | null): void {
  hardAuthFailureHandler = handler;
}

// Dedupes concurrent refreshes: if several requests hit the expired token around the same
// moment (e.g. two profile cards saving back-to-back), only one /api/auth/refresh call is
// made and every caller awaits that same result instead of racing separate ones.
let refreshInFlight: Promise<boolean> | null = null;

/** Raw fetch, not apiFetch — apiFetch itself calls this on a 401, so going through apiFetch
 *  here would recurse. Never logs or otherwise exposes the token; a failure is just `false`. */
function refreshAccessToken(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = fetch(`${BASE_URL}/api/auth/refresh`, {
      method: 'POST',
      headers: { Accept: 'application/json' },
      credentials: 'include', // the HttpOnly refresh cookie, never touched by JS
    })
      .then(async (response) => {
        if (!response.ok) return false;
        const payload: unknown = await response.json().catch(() => null);
        const newToken = (payload as { accessToken?: string } | null)?.accessToken;
        if (!newToken) return false;
        setAccessToken(newToken);
        return true;
      })
      .catch(() => false)
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

/**
 * On a 401, decides whether it's worth retrying: only for AUTH_TOKEN_INVALID (this specific
 * access token — most often just expired), never for AUTH_TOKEN_MISSING or an authorization
 * failure on a resource the caller genuinely can't access, which retrying would only mask.
 * Reads the body via `.clone()` so the original, unconsumed response is still available to
 * the caller if no retry happens.
 */
async function shouldRetryAfterRefresh(response: Response): Promise<boolean> {
  if (response.status !== 401) return false;
  const payload: unknown = await response
    .clone()
    .json()
    .catch(() => null);
  if ((payload as { code?: string } | null)?.code !== 'AUTH_TOKEN_INVALID') return false;

  const refreshed = await refreshAccessToken();
  if (!refreshed) {
    // The refresh cookie is gone too — this is a real, expired session, not a renewable
    // access token. Drop the dead token and let the rest of the app know re-auth is actually
    // required (ProtectedRoute already redirects once the session query reflects that).
    setAccessToken(null);
    hardAuthFailureHandler?.();
  }
  return refreshed;
}

function buildHeaders(init: RequestInit): Headers {
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json');
  if (init.body && !(init.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }
  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`);
  }
  return headers;
}

function errorBodyFallback(path: string, status: number): ApiErrorBody {
  return {
    timestamp: new Date().toISOString(),
    status,
    code: 'UNKNOWN_ERROR',
    message: 'Request failed.',
    path,
  };
}

export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response = await fetch(`${BASE_URL}${path}`, {
    ...init,
    headers: buildHeaders(init),
    credentials: 'include', // carries the HttpOnly refresh cookie
  });

  if (response.status === 401 && (await shouldRetryAfterRefresh(response))) {
    response = await fetch(`${BASE_URL}${path}`, {
      ...init,
      headers: buildHeaders(init), // re-built: picks up the just-refreshed access token
      credentials: 'include',
    });
  }

  if (response.status === 204) return undefined as T;

  const payload: unknown = await response.json().catch(() => null);

  if (!response.ok) {
    throw new ApiError(response.status, (payload as ApiErrorBody) ?? errorBodyFallback(path, response.status));
  }

  return payload as T;
}

/**
 * For binary responses (a rendered PDF) — apiFetch always parses JSON, which a PDF isn't.
 * Same auth/credentials handling, including the expired-token retry above, but on a non-OK
 * response it still tries to parse the standard JSON error envelope so callers get the same
 * ApiError shape either way.
 */
export async function apiFetchBlob(path: string, init: RequestInit = {}): Promise<Blob> {
  let response = await fetch(`${BASE_URL}${path}`, {
    ...init,
    headers: buildHeaders(init),
    credentials: 'include',
  });

  if (response.status === 401 && (await shouldRetryAfterRefresh(response))) {
    response = await fetch(`${BASE_URL}${path}`, {
      ...init,
      headers: buildHeaders(init),
      credentials: 'include',
    });
  }

  if (!response.ok) {
    const payload: unknown = await response.json().catch(() => null);
    throw new ApiError(response.status, (payload as ApiErrorBody) ?? errorBodyFallback(path, response.status));
  }

  return response.blob();
}
