/**
 * Centralized API client. Handles auth token injection and error unwrapping.
 * Never exposes backend secrets.
 */
import type { ApiError } from '../types';
import { refreshAccessToken } from '../auth/refresh';
import { tokenStorage } from '../auth/tokenStorage';

// Empty = same origin; the Vite dev proxy forwards /api to the backend.
const BASE = import.meta.env.VITE_API_URL ?? '';

export class ApiRequestError extends Error {
  readonly status: number;
  readonly code?: string;

  constructor(status: number, message: string, code?: string) {
    super(message);
    this.name = 'ApiRequestError';
    this.status = status;
    this.code = code;
  }
}

function getToken(): string | null {
  return tokenStorage.getAccess();
}

/** The session is really over: drop it and go to the sign-in page. */
function endSession(): void {
  tokenStorage.clear();
  if (!window.location.pathname.startsWith('/auth')) {
    window.location.assign('/auth');
  }
}

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
  retried = false,
): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };
  const token = getToken();
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const res = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: body != null ? JSON.stringify(body) : undefined,
    credentials: 'include',
  });

  // Expired access token: renew silently and repeat the request once.
  if (res.status === 401 && !retried && token && !path.startsWith('/api/v1/auth/')) {
    let renewed: string | null = null;
    try {
      renewed = await refreshAccessToken();
    } catch {
      // Offline or server down: keep the session, surface the failure.
      throw new ApiRequestError(0, 'Network error, please try again');
    }
    if (renewed) return request<T>(method, path, body, true);
    endSession();
    throw new ApiRequestError(401, 'Your session has expired', 'SESSION_EXPIRED');
  }

  if (res.status === 204) {
    return undefined as T;
  }

  const data = await res.json().catch(() => null);

  if (!res.ok) {
    const err = data as ApiError | null;
    throw new ApiRequestError(
      res.status,
      err?.message ?? err?.code ?? 'Request failed',
      err?.code
    );
  }

  return data as T;
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
  put: <T>(path: string, body?: unknown) => request<T>('PUT', path, body),
  patch: <T>(path: string, body?: unknown) => request<T>('PATCH', path, body),
  delete: (path: string) => request<void>('DELETE', path),
};
