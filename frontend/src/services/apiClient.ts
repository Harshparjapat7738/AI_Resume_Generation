/**
 * Thin fetch wrapper for the API Gateway.
 *
 * Every request goes to the gateway; the browser never holds a Groq key, a MongoDB URI
 * or S3 credentials. The refresh token lives in an HttpOnly cookie and is never readable
 * from JavaScript.
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

export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json');
  if (init.body && !(init.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }
  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`);
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    ...init,
    headers,
    credentials: 'include', // carries the HttpOnly refresh cookie
  });

  if (response.status === 204) return undefined as T;

  const payload: unknown = await response.json().catch(() => null);

  if (!response.ok) {
    throw new ApiError(response.status, (payload as ApiErrorBody) ?? {
      timestamp: new Date().toISOString(),
      status: response.status,
      code: 'UNKNOWN_ERROR',
      message: 'Request failed.',
      path,
    });
  }

  return payload as T;
}

/**
 * For binary responses (a rendered PDF) — apiFetch always parses JSON, which a PDF isn't.
 * Same auth/credentials handling, but on a non-OK response it still tries to parse the
 * standard JSON error envelope so callers get the same ApiError shape either way.
 */
export async function apiFetchBlob(path: string, init: RequestInit = {}): Promise<Blob> {
  const headers = new Headers(init.headers);
  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`);
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    ...init,
    headers,
    credentials: 'include',
  });

  if (!response.ok) {
    const payload: unknown = await response.json().catch(() => null);
    throw new ApiError(response.status, (payload as ApiErrorBody) ?? {
      timestamp: new Date().toISOString(),
      status: response.status,
      code: 'UNKNOWN_ERROR',
      message: 'Request failed.',
      path,
    });
  }

  return response.blob();
}
