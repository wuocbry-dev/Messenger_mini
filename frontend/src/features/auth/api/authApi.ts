import { api } from '../../../shared/api';
import type { User } from '../../../shared/types';
import { saveAuth } from '../store/authStore';
import { tokenStorage } from '../../../shared/auth/tokenStorage';

export interface LoginRequest {
  usernameOrEmail: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  displayName: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  user: User;
}

export interface UpdateProfileRequest {
  displayName: string;
  /** Object key of an uploaded image; '' removes the avatar; omit to clear. Send the current key to keep it. */
  avatarObjectKey?: string;
}

export const authApi = {
  register(data: RegisterRequest): Promise<AuthResponse> {
    return api.post<AuthResponse>('/api/v1/auth/register', data);
  },

  login(data: LoginRequest): Promise<AuthResponse> {
    return api.post<AuthResponse>('/api/v1/auth/login', data);
  },

  logout(): Promise<void> {
    const refreshToken = tokenStorage.getRefresh();
    // Local sign-out must always succeed, even if the server call fails.
    const done = refreshToken
      ? api.post<void>('/api/v1/auth/logout', { refreshToken }).catch(() => {})
      : Promise.resolve();
    return done.finally(() => tokenStorage.clear());
  },

  refresh(refreshToken: string): Promise<{ accessToken: string }> {
    return api.post('/api/v1/auth/refresh', { refreshToken });
  },

  me(): Promise<User> {
    return api.get<User>('/api/v1/users/me');
  },

  updateProfile(data: UpdateProfileRequest): Promise<User> {
    return api.patch<User>('/api/v1/users/me/profile', data);
  },

  getCurrentUser(): User | null {
    const raw = tokenStorage.getUserJson();
    if (!raw) return null;
    try {
      return JSON.parse(raw) as User;
    } catch {
      return null;
    }
  },

  persistLogin(response: AuthResponse): void {
    saveAuth(response.accessToken, response.user, response.refreshToken);
  },
};
