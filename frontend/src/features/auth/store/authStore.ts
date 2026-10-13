/**
 * Auth store — simple in-memory state.
 * In a larger app this would be a proper state manager.
 */
import { createContext, useContext } from 'react';
import type { User } from '../../../shared/types';

export interface AuthState {
  user: User | null;
  accessToken: string | null;
  isAuthenticated: boolean;
}

export const AUTH_TOKEN_KEY = 'accessToken';
const AUTH_USER_KEY = 'authUser';
const REFRESH_TOKEN_KEY = 'refreshToken';

export function loadAuthFromStorage(): AuthState {
  const token = sessionStorage.getItem(AUTH_TOKEN_KEY);
  const userRaw = sessionStorage.getItem(AUTH_USER_KEY);
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
    isAuthenticated: !!token && !!user,
  };
}

export function saveAuth(token: string, user: User): void {
  sessionStorage.setItem(AUTH_TOKEN_KEY, token);
  sessionStorage.setItem(AUTH_USER_KEY, JSON.stringify(user));
}

export function clearAuth(): void {
  sessionStorage.removeItem(AUTH_TOKEN_KEY);
  sessionStorage.removeItem(AUTH_USER_KEY);
  sessionStorage.removeItem(REFRESH_TOKEN_KEY);
}

export const AuthContext = createContext<AuthState>(loadAuthFromStorage());

export function useAuth(): AuthState {
  return useContext(AuthContext);
}
