import { jwtExpiryMs, tokenStorage } from './tokenStorage';

// Empty = same origin; the Vite dev proxy forwards /api to the backend.
const BASE = import.meta.env.VITE_API_URL ?? '';

/** Refresh when fewer than this many ms remain on the access token. */
const REFRESH_MARGIN_MS = 2 * 60 * 1000;

interface RefreshResponse {
  accessToken: string;
  refreshToken: string;
}

let inFlight: Promise<string | null> | null = null;

/**
 * Exchange the refresh token for a new pair. The server rotates refresh
 * tokens, so concurrent calls share one request (and another tab refreshing at
 * the same moment is detected by re-reading storage).
 *
 * Resolves to the new access token, or null if the session is really over
 * (refresh token missing, expired or revoked). A network failure throws, so the
 * caller does NOT sign the user out just because they are offline.
 */
export function refreshAccessToken(): Promise<string | null> {
  if (inFlight) return inFlight;
  inFlight = doRefresh().finally(() => {
    inFlight = null;
  });
  return inFlight;
}

async function doRefresh(): Promise<string | null> {
  const used = tokenStorage.getRefresh();
  if (!used) return null;

  const res = await fetch(`${BASE}/api/v1/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken: used }),
  });

  if (res.ok) {
    const data = (await res.json()) as RefreshResponse;
    tokenStorage.setTokens(data.accessToken, data.refreshToken);
    return data.accessToken;
  }

  if (res.status === 401 || res.status === 400) {
    // Another tab may have rotated the token a moment ago; use its result.
    const current = tokenStorage.getRefresh();
    if (current && current !== used) return tokenStorage.getAccess();
    return null;
  }

  throw new Error(`Token refresh failed (${res.status})`);
}

/** Refresh ahead of expiry so requests rarely hit a 401 at all. */
export async function ensureFreshToken(): Promise<void> {
  const access = tokenStorage.getAccess();
  if (!access) return;
  const exp = jwtExpiryMs(access);
  if (exp !== null && exp - Date.now() > REFRESH_MARGIN_MS) return;
  try {
    await refreshAccessToken();
  } catch {
    /* offline: try again later */
  }
}

/**
 * Keeps the session alive while the app is open: schedules a refresh shortly
 * before expiry and re-checks when the tab returns or the network comes back
 * (timers are throttled/paused while a laptop sleeps). Returns a stop function.
 */
export function startTokenKeepAlive(onSessionEnded: () => void): () => void {
  let timer: ReturnType<typeof setTimeout> | null = null;
  let stopped = false;

  const check = async () => {
    if (stopped) return;
    const before = tokenStorage.getAccess();
    if (!before) return;
    try {
      const exp = jwtExpiryMs(before);
      if (exp === null || exp - Date.now() <= REFRESH_MARGIN_MS) {
        const next = await refreshAccessToken();
        if (next === null) {
          onSessionEnded();
          return;
        }
      }
    } catch {
      /* offline: retry on the next tick */
    }
    schedule();
  };

  const schedule = () => {
    if (stopped) return;
    if (timer) clearTimeout(timer);
    const exp = jwtExpiryMs(tokenStorage.getAccess());
    const wait = exp === null ? 60_000 : Math.min(Math.max(exp - Date.now() - REFRESH_MARGIN_MS, 5_000), 10 * 60_000);
    timer = setTimeout(() => void check(), wait);
  };

  const onWake = () => {
    if (document.visibilityState === 'hidden') return;
    void check();
  };

  document.addEventListener('visibilitychange', onWake);
  window.addEventListener('online', onWake);
  void check();

  return () => {
    stopped = true;
    if (timer) clearTimeout(timer);
    document.removeEventListener('visibilitychange', onWake);
    window.removeEventListener('online', onWake);
  };
}
