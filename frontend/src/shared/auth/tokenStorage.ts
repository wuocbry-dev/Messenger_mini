/**
 * Persistent token storage. Tokens live in localStorage so a user stays signed
 * in across tabs and browser restarts (like Facebook) until they sign out or
 * the refresh token expires.
 *
 * Trade-off: localStorage is readable by any script on the page, so the app
 * must stay free of XSS (React escapes output; never render untrusted HTML).
 */
const ACCESS_KEY = 'accessToken';
const REFRESH_KEY = 'refreshToken';
const USER_KEY = 'authUser';

function read(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function write(key: string, value: string): void {
  try {
    localStorage.setItem(key, value);
  } catch {
    /* storage unavailable (private mode / quota) */
  }
}

function remove(key: string): void {
  try {
    localStorage.removeItem(key);
  } catch {
    /* ignore */
  }
}

/** One-time move of a session from the old sessionStorage location. */
function migrateLegacySession(): void {
  try {
    if (localStorage.getItem(ACCESS_KEY) || !sessionStorage.getItem(ACCESS_KEY)) return;
    for (const key of [ACCESS_KEY, REFRESH_KEY, USER_KEY]) {
      const value = sessionStorage.getItem(key);
      if (value) localStorage.setItem(key, value);
      sessionStorage.removeItem(key);
    }
  } catch {
    /* ignore */
  }
}
migrateLegacySession();

export const tokenStorage = {
  getAccess: () => read(ACCESS_KEY),
  getRefresh: () => read(REFRESH_KEY),
  getUserJson: () => read(USER_KEY),
  setTokens(access: string, refresh?: string): void {
    write(ACCESS_KEY, access);
    if (refresh) write(REFRESH_KEY, refresh);
  },
  setUserJson: (json: string) => write(USER_KEY, json),
  clear(): void {
    remove(ACCESS_KEY);
    remove(REFRESH_KEY);
    remove(USER_KEY);
  },
};

/** Expiry (epoch ms) from a JWT's `exp` claim, or null if unreadable. */
export function jwtExpiryMs(token: string | null): number | null {
  if (!token) return null;
  try {
    const payload = token.split('.')[1];
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    const exp = (JSON.parse(json) as { exp?: number }).exp;
    return typeof exp === 'number' ? exp * 1000 : null;
  } catch {
    return null;
  }
}
