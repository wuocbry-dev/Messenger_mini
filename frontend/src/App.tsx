import React, { useEffect, useState } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AppLayout } from './app/AppLayout';
import { MessengerPage } from './app/MessengerPage';
import { AuthPage } from './features/auth/pages/AuthPage';
import { UsersPage } from './features/chat/pages/UsersPage';
import { SettingsPage } from './features/user/pages/SettingsPage';
import { CallsPage } from './features/call/pages/CallsPage';
import { authApi } from './features/auth/api/authApi';
import {
  AUTH_USER_UPDATED_EVENT,
  clearAuth,
  loadAuthFromStorage,
  updateStoredUser,
} from './features/auth/store/authStore';
import { RealtimeProvider } from './shared/realtime/RealtimeProvider';
import { startTokenKeepAlive } from './shared/auth/refresh';
import type { User } from './shared/types';
import './shared/styles/global.css';

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const auth = loadAuthFromStorage();
  if (!auth.isAuthenticated) {
    return <Navigate to="/auth" replace />;
  }
  return <>{children}</>;
}

function PublicRoute({ children }: { children: React.ReactNode }) {
  const auth = loadAuthFromStorage();
  if (auth.isAuthenticated) {
    return <Navigate to="/" replace />;
  }
  return <>{children}</>;
}

/** Presigned avatar URLs last ~15 min; renew well before that. */
const AVATAR_REFRESH_MS = 10 * 60 * 1000;

function ChatLayout() {
  const [user, setUser] = useState<User | null>(() => loadAuthFromStorage().user);

  // Keep the session alive: refresh the access token before it expires and
  // whenever the tab wakes up; sign out only if the refresh token is rejected.
  useEffect(
    () =>
      startTokenKeepAlive(() => {
        clearAuth();
        window.location.assign('/auth');
      }),
    []
  );

  useEffect(() => {
    const onUpdated = (e: Event) => setUser((e as CustomEvent<User>).detail);
    window.addEventListener(AUTH_USER_UPDATED_EVENT, onUpdated);
    // The avatar is a presigned URL that expires after ~15 minutes, and the
    // cached login payload has none, so fetch it now and keep it fresh.
    let last = 0;
    const refreshMe = () => {
      last = Date.now();
      authApi.me().then(updateStoredUser).catch(() => {});
    };
    const onVisible = () => {
      if (document.visibilityState === 'visible' && Date.now() - last > AVATAR_REFRESH_MS / 2) {
        refreshMe();
      }
    };
    refreshMe();
    const timer = setInterval(refreshMe, AVATAR_REFRESH_MS);
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      clearInterval(timer);
      document.removeEventListener('visibilitychange', onVisible);
      window.removeEventListener(AUTH_USER_UPDATED_EVENT, onUpdated);
    };
  }, []);

  return (
    <RealtimeProvider>
      <AppLayout user={user} />
    </RealtimeProvider>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route
          path="/auth"
          element={<PublicRoute><AuthPage /></PublicRoute>}
        />
        <Route element={<ProtectedRoute><ChatLayout /></ProtectedRoute>}>
          <Route path="/" element={<MessengerPage />} />
          <Route path="/users" element={<UsersPage />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="/calls" element={<CallsPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
