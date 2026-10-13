import React, { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Avatar } from '../shared/components/ui/Avatar';
import { authApi } from '../features/auth/api/authApi';
import { clearAuth } from '../features/auth/store/authStore';
import './AppUserCard.css';

interface AppUserCardProps {
  user: {
    displayName: string;
    username: string;
    email?: string;
    avatarUrl?: string;
  } | null;
}

/** Signed-in account at the bottom of the left card, with a sign-out menu. */
export function AppUserCard({ user }: AppUserCardProps) {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [signingOut, setSigningOut] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);

  // Close the menu on outside click or Escape.
  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const handleLogout = async () => {
    if (signingOut) return;
    setSigningOut(true);
    try {
      await authApi.logout(); // always clears the local session
    } catch {
      /* ignore: signing out locally is what matters */
    }
    clearAuth();
    navigate('/auth', { replace: true });
  };

  if (!user) return null;

  return (
    <div className="user-card-footer" ref={rootRef}>
      <div className="user-card-footer-info">
        <Avatar name={user.displayName} src={user.avatarUrl} size="md" />
        <div className="user-card-footer-text">
          <span className="user-card-footer-name">{user.displayName}</span>
          <span className="user-card-footer-sub">{user.email ?? `@${user.username}`}</span>
        </div>
      </div>

      <button
        className="user-card-footer-more"
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label="Account menu"
        title="Account menu"
      >
        <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
          <circle cx="5" cy="12" r="1.8" />
          <circle cx="12" cy="12" r="1.8" />
          <circle cx="19" cy="12" r="1.8" />
        </svg>
      </button>

      {open && (
        <div className="user-card-footer-menu" role="menu">
          <button
            role="menuitem"
            onClick={() => {
              setOpen(false);
              navigate('/settings');
            }}
          >
            Profile settings
          </button>
          <button role="menuitem" className="user-card-footer-menu-danger" onClick={() => void handleLogout()} disabled={signingOut}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
              <polyline points="16 17 21 12 16 7" />
              <line x1="21" y1="12" x2="9" y2="12" />
            </svg>
            {signingOut ? 'Signing out...' : 'Sign out'}
          </button>
        </div>
      )}
    </div>
  );
}
