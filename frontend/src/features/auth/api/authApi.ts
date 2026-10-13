import { api } from '../../../shared/api';
import type { User } from '../../../shared/types';
import { saveAuth } from '../store/authStore';

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
  displayName?: string;
}

export const authApi = {
  register(data: RegisterRequest): Promise<AuthResponse> {
    return api.post<AuthResponse>('/api/v1/auth/register', data);
  },

  login(data: LoginRequest): Promise<AuthResponse> {
    return api.post<AuthResponse>('/api/v1/auth/login', data);
  },

  logout(): Promise<void> {
    const refreshToken = sessionStorage.getItem('refreshToken');
    sessionStorage.removeItem('refreshToken');
    return api.post('/api/v1/auth/logout', { refreshToken });
  },

  refresh(refreshToken: string): Promise<{ accessToken: string }> {
    return api.post('/api/v1/auth/refresh', { refreshToken });
  },

  me(): Promise<User> {
    return api.get<User>('/api/v1/users/me');
  },

  updateProfile(data: UpdateProfileRequest): Promise<User> {
    return api.patch<User>('/api/v1/users/me', data);
  },

  getCurrentUser(): User | null {
    const raw = sessionStorage.getItem('authUser');
    if (!raw) return null;
    try {
      return JSON.parse(raw) as User;
    } catch {
      return null;
    }
  },

  persistLogin(response: AuthResponse): void {
    sessionStorage.setItem('refreshToken', response.refreshToken);
    saveAuth(response.accessToken, response.user);
  },
};
