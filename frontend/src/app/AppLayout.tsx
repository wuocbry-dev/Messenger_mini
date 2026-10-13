import React, { useState } from 'react';
import { Outlet } from 'react-router-dom';
import { AppSidebar } from './AppSidebar';
import { SidebarSlotContext } from './SidebarSlot';
import './AppLayout.css';

interface AppLayoutProps {
  user: { id: number; username: string; displayName: string; avatarUrl?: string } | null;
}

export function AppLayout({ user }: AppLayoutProps) {
  const [slot, setSlot] = useState<HTMLElement | null>(null);

  return (
    <div className="app-layout">
      <aside className="app-left-card">
        <AppSidebar user={user} />
        <div ref={setSlot} className="app-left-slot" />
      </aside>
      <main className="app-main">
        <SidebarSlotContext.Provider value={slot}>
          <Outlet />
        </SidebarSlotContext.Provider>
      </main>
    </div>
  );
}
