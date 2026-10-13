import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AppLayout } from './app/AppLayout';
import { MessengerPage } from './app/MessengerPage';
import { AuthPage } from './features/auth/pages/AuthPage';
import { UsersPage } from './features/chat/pages/UsersPage';
import { SettingsPage } from './features/user/pages/SettingsPage';
import { CallsPage } from './features/call/pages/CallsPage';
import { loadAuthFromStorage } from './features/auth/store/authStore';
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
  const auth = loadAuthFromStorage();
  const user = auth.user;
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
