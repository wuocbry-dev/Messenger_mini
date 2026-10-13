/**
 * Auth store — simple in-memory state.
 * In a larger app this would be a proper state manager.
 */
import { createContext, useContext } from 'react';
import { tokenStorage } from '../../../shared/auth/tokenStorage';
import type { User } from '../../../shared/types';

export interface AuthState {
  user: User | null;
  accessToken: string | null;
  isAuthenticated: boolean;
}

export const AUTH_USER_UPDATED_EVENT = 'auth:user-updated';

export function loadAuthFromStorage(): AuthState {
  const token = tokenStorage.getAccess();
  const userRaw = tokenStorage.getUserJson();
  let user: User | null = null;
  if (userRaw) {
    try {
      user = JSON.parse(userRaw) as User;
    } catch {
      clearAuth();
    }
  }
  return {
    user,
    accessToken: token,
    // An expired access token is fine: the refresh token renews it silently.
    isAuthenticated: !!token && !!user,
  };
}

export function saveAuth(token: string, user: User, refreshToken?: string): void {
  tokenStorage.setTokens(token, refreshToken);
  tokenStorage.setUserJson(JSON.stringify(user));
}

/** Replace the cached user (e.g. after a profile edit) and notify listeners. */
export function updateStoredUser(user: User): void {
  tokenStorage.setUserJson(JSON.stringify(user));
  window.dispatchEvent(new CustomEvent<User>(AUTH_USER_UPDATED_EVENT, { detail: user }));
}

export function clearAuth(): void {
  tokenStorage.clear();
}

export const AuthContext = createContext<AuthState>(loadAuthFromStorage());

export function useAuth(): AuthState {
  return useContext(AuthContext);
}
