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

/**
 * Built only when the server's response body isn't the expected JSON error envelope — which
 * never happens for a request that actually reached one of our own services (every one of them
 * always answers through platform-common's GlobalExceptionHandler, JSON, guaranteed). Seeing
 * this fallback at all is itself the diagnostic: something else answered instead — a dev-server
 * proxy, a corporate/school network filter or antivirus doing HTTP inspection, a captive portal,
 * or a load balancer's own error page. `code` names that distinction explicitly rather than the
 * old generic "unknown", and the message carries the one clue that actually narrows it down: what
 * shape of thing responded, and a safe preview of it — never the guess "request failed" with
 * nothing to act on.
 */
function errorBodyFallback(path: string, status: number, contentType: string | null, rawText: string): ApiErrorBody {
  const preview = rawText
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 200);
  const previewSuffix = rawText.length > 200 ? '…' : '';

  const hint = /html/i.test(contentType ?? '')
    ? ' The response was an HTML page, not JSON — almost always a proxy, security software, or a captive-portal page intercepting the request rather than this app\'s own server responding.'
    : rawText.trim() === ''
      ? ' The response body was empty.'
      : '';

  return {
    timestamp: new Date().toISOString(),
    status,
    code: 'NON_JSON_RESPONSE',
    message: `Server responded with status ${status}, but the body wasn't the expected JSON`
      + ` (content-type: ${contentType ?? 'none'}).${hint}`
      + (preview ? ` Response preview: "${preview}${previewSuffix}"` : ''),
    path,
  };
}

/** Reads the response body as text once, then tries to parse it as JSON — unlike calling
 *  `response.json()` directly, a parse failure here still leaves the raw text (and content
 *  type) available for {@link errorBodyFallback} instead of being silently discarded. */
async function parseJsonBody(response: Response): Promise<{ payload: unknown; rawText: string }> {
  const rawText = await response.text();
  if (rawText === '') {
    return { payload: null, rawText };
  }
  try {
    return { payload: JSON.parse(rawText), rawText };
  } catch {
    return { payload: null, rawText };
  }
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

  const { payload, rawText } = await parseJsonBody(response);

  if (!response.ok) {
    throw new ApiError(
      response.status,
      (payload as ApiErrorBody | null) ??
        errorBodyFallback(path, response.status, response.headers.get('content-type'), rawText),
    );
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
    const { payload, rawText } = await parseJsonBody(response);
    throw new ApiError(
      response.status,
      (payload as ApiErrorBody | null) ??
        errorBodyFallback(path, response.status, response.headers.get('content-type'), rawText),
    );
  }

  return response.blob();
}
