import React, { useState, useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { MessengerPage } from './app/MessengerPage';
import { AuthPage } from './features/auth/pages/AuthPage';
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

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route
          path="/auth"
          element={<PublicRoute><AuthPage /></PublicRoute>}
        />
        <Route
          path="/"
          element={<ProtectedRoute><MessengerPage /></ProtectedRoute>}
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
