import React, { useEffect, useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { AppSidebar } from './AppSidebar';
import { AppUserCard } from './AppUserCard';
import { MobileTabBar } from './MobileTabBar';
import { SidebarSlotContext } from './SidebarSlot';
import { useIsMobile } from '../shared/hooks/useIsMobile';
import './AppLayout.css';

interface AppLayoutProps {
  user: { id: number; username: string; displayName: string; email?: string; avatarUrl?: string } | null;
}

/**
 * iOS Safari does not shrink `dvh` for the on-screen keyboard, so on phones the
 * app height follows the visual viewport to keep the composer above the keyboard.
 */
function useVisualViewportHeight(enabled: boolean) {
  useEffect(() => {
    const vv = window.visualViewport;
    const root = document.documentElement;
    if (!enabled || !vv) {
      root.style.removeProperty('--app-height');
      return;
    }
    const update = () => {
      root.style.setProperty('--app-height', `${Math.round(vv.height)}px`);
      // The browser may scroll the page to reveal a focused input; the app
      // scrolls its own panes, so pin the page back to the top.
      if (window.scrollY !== 0) window.scrollTo(0, 0);
    };
    update();
    vv.addEventListener('resize', update);
    return () => {
      vv.removeEventListener('resize', update);
      root.style.removeProperty('--app-height');
    };
  }, [enabled]);
}

export function AppLayout({ user }: AppLayoutProps) {
  const [slot, setSlot] = useState<HTMLElement | null>(null);
  const isMobile = useIsMobile();
  const { pathname } = useLocation();
  useVisualViewportHeight(isMobile);

  return (
    <div className="app-layout">
      <aside className="app-left-card">
        <AppSidebar />
        <div ref={setSlot} className="app-left-slot" />
        <AppUserCard user={user} />
      </aside>
      <main className="app-main">
        {/* Phones have no left card, so the account card (and sign-out) sits on Settings. */}
        {isMobile && pathname === '/settings' && (
          <div className="app-mobile-account">
            <AppUserCard user={user} />
          </div>
        )}
        {/* On phones ChatPanel mounts the conversation list inline instead of in the left card. */}
        <SidebarSlotContext.Provider value={isMobile ? null : slot}>
          <Outlet />
        </SidebarSlotContext.Provider>
      </main>
      <MobileTabBar />
    </div>
  );
}
