import React from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../features/auth/api/authApi';
import { Avatar, IconButton } from '../shared/components/ui';
import './AppLayout.css';

interface AppLayoutProps {
  children: React.ReactNode;
  user: { id: number; username: string; displayName: string; avatarUrl?: string } | null;
}

export function AppLayout({ children, user }: AppLayoutProps) {
  const navigate = useNavigate();

  const handleLogout = async () => {
    try {
      await authApi.logout();
    } catch { /* ignore */ }
    sessionStorage.clear();
    navigate('/auth');
  };

  return (
    <div className="app-layout">
      {children}
      <footer className="app-footer">
        <div className="app-footer-user">
          {user && (
            <>
              <Avatar name={user.displayName} src={user.avatarUrl} size="sm" />
              <span className="app-footer-name">{user.displayName}</span>
            </>
          )}
        </div>
        <IconButton label="Sign out" variant="ghost" size="sm" onClick={handleLogout}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
            <polyline points="16 17 21 12 16 7" />
            <line x1="21" y1="12" x2="9" y2="12" />
          </svg>
        </IconButton>
      </footer>
    </div>
  );
}
