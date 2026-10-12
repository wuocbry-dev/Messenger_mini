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

export function loadAuthFromStorage(): AuthState {
  const token = sessionStorage.getItem(AUTH_TOKEN_KEY);
  const userRaw = sessionStorage.getItem('authUser');
  const user = userRaw ? (JSON.parse(userRaw) as User) : null;
  return {
    user,
    accessToken: token,
    isAuthenticated: !!token && !!user,
  };
}

export function saveAuth(token: string, user: User): void {
  sessionStorage.setItem(AUTH_TOKEN_KEY, token);
  sessionStorage.setItem('authUser', JSON.stringify(user));
}

export function clearAuth(): void {
  sessionStorage.removeItem(AUTH_TOKEN_KEY);
  sessionStorage.removeItem('authUser');
}

export const AuthContext = createContext<AuthState>(loadAuthFromStorage());

export function useAuth(): AuthState {
  return useContext(AuthContext);
}
