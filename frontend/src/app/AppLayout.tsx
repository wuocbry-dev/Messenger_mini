import React from 'react';
import { Outlet } from 'react-router-dom';
import { AppSidebar } from './AppSidebar';
import './AppLayout.css';

interface AppLayoutProps {
  user: { id: number; username: string; displayName: string; avatarUrl?: string } | null;
}

export function AppLayout({ user }: AppLayoutProps) {
  return (
    <div className="app-layout">
      <AppSidebar user={user} />
      <main className="app-main">
        <Outlet />
      </main>
    </div>
  );
}
