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
  loadAuthFromStorage,
  updateStoredUser,
} from './features/auth/store/authStore';
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

function ChatLayout() {
  const [user, setUser] = useState<User | null>(() => loadAuthFromStorage().user);

  useEffect(() => {
    const onUpdated = (e: Event) => setUser((e as CustomEvent<User>).detail);
    window.addEventListener(AUTH_USER_UPDATED_EVENT, onUpdated);
    // The cached login payload has no avatar URL; refresh it once.
    authApi.me().then(updateStoredUser).catch(() => {});
    return () => window.removeEventListener(AUTH_USER_UPDATED_EVENT, onUpdated);
  }, []);

  return <AppLayout user={user} />;
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
