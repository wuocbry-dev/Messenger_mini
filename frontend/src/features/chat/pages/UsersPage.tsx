import React, { useState, useCallback, useEffect, useMemo, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Avatar } from '../../../shared/components/ui/Avatar';
import { EmptyState } from '../../../shared/components/ui/EmptyState';
import { Skeleton } from '../../../shared/components/ui/Skeleton';
import { Alert } from '../../../shared/components/ui/Alert';
import { userApi } from '../api/userApi';
import { conversationApi } from '../api/conversationApi';
import { parseUtc } from '../../../shared/utils/datetime';
import { useRealtime } from '../../../shared/realtime/RealtimeProvider';
import { useNow } from '../../../shared/hooks/useNow';
import type { UserSearchResult } from '../../../shared/types';
import './UsersPage.css';

type Filter = 'all' | 'online' | 'recent';
type Sort = 'az' | 'za' | 'recent';
type View = 'grid' | 'list';

const RECENT_WINDOW_MS = 24 * 60 * 60 * 1000;
const REFRESH_MS = 30_000;

function lastActiveAt(user: UserSearchResult): number {
  if (user.online) return Number.MAX_SAFE_INTEGER;
  return parseUtc(user.lastSeenAt) ?? 0;
}

function isRecent(user: UserSearchResult): boolean {
  if (user.online) return true;
  const t = parseUtc(user.lastSeenAt);
  return t !== null && Date.now() - t <= RECENT_WINDOW_MS;
}

function statusText(user: UserSearchResult): string {
  if (user.online) return 'Online';
  const t = parseUtc(user.lastSeenAt);
  if (t === null) return 'Offline';
  const mins = Math.max(0, Math.floor((Date.now() - t) / 60000));
  if (mins < 1) return 'Active just now';
  if (mins < 60) return `Active ${mins}m ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `Active ${hours}h ago`;
  const days = Math.floor(hours / 24);
  return days < 7 ? `Active ${days}d ago` : 'Offline';
}

function lastSeenDate(user: UserSearchResult): string {
  const t = parseUtc(user.lastSeenAt);
  return t === null ? 'Never' : new Date(t).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' });
}

export function UsersPage() {
  const navigate = useNavigate();
  const [baseUsers, setUsers] = useState<UserSearchResult[]>([]);
  const { presence } = useRealtime();
  useNow(30_000); // re-render so "Active 5m ago" keeps counting

  // Live presence events win over the last REST snapshot.
  const users = useMemo(
    () =>
      baseUsers.map((u) => {
        const live = presence.get(u.id);
        return live ? { ...u, online: live.online, lastSeenAt: live.lastSeenAt ?? u.lastSeenAt } : u;
      }),
    [baseUsers, presence]
  );
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState<Filter>('all');
  const [sort, setSort] = useState<Sort>('az');
  const [view, setView] = useState<View>('grid');
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [menuId, setMenuId] = useState<number | null>(null);
  const [creatingConv, setCreatingConv] = useState<number | null>(null);
  const [copied, setCopied] = useState<number | null>(null);
  const loadedOnce = useRef(false);

  // Load, then refresh quietly so online status stays reasonably current.
  useEffect(() => {
    let cancelled = false;
    const load = () =>
      userApi
        .list()
        .then((data) => {
          if (cancelled) return;
          setUsers(data);
          setError(null);
        })
        .catch((err) => {
          if (!cancelled && !loadedOnce.current) {
            setError(err instanceof Error ? err.message : 'Failed to load users');
          }
        })
        .finally(() => {
          if (cancelled) return;
          loadedOnce.current = true;
          setLoading(false);
        });
    void load();
    const timer = setInterval(() => void load(), REFRESH_MS);
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, []);

  // Close the "..." menu on outside click or Escape.
  useEffect(() => {
    if (menuId === null) return;
    const close = () => setMenuId(null);
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && close();
    document.addEventListener('click', close);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('click', close);
      document.removeEventListener('keydown', onKey);
    };
  }, [menuId]);

  const counts = useMemo(
    () => ({
      all: users.length,
      online: users.filter((u) => u.online).length,
      recent: users.filter(isRecent).length,
    }),
    [users]
  );

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    const list = users.filter((u) => {
      if (filter === 'online' && !u.online) return false;
      if (filter === 'recent' && !isRecent(u)) return false;
      return (
        !q || u.displayName.toLowerCase().includes(q) || u.username.toLowerCase().includes(q)
      );
    });
    const byName = (a: UserSearchResult, b: UserSearchResult) =>
      a.displayName.localeCompare(b.displayName, undefined, { sensitivity: 'base' });
    return [...list].sort((a, b) =>
      sort === 'za' ? byName(b, a) : sort === 'recent' ? lastActiveAt(b) - lastActiveAt(a) || byName(a, b) : byName(a, b)
    );
  }, [users, query, filter, sort]);

  const selected = users.find((u) => u.id === selectedId) ?? null;

  const handleMessage = useCallback(
    async (userId: number) => {
      setCreatingConv(userId);
      setError(null);
      try {
        const conv = await conversationApi.create(userId);
        navigate('/', { state: { openConversationId: conv.id } });
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to start conversation');
      } finally {
        setCreatingConv(null);
      }
    },
    [navigate]
  );

  const handleCopy = useCallback(async (user: UserSearchResult) => {
    try {
      await navigator.clipboard.writeText(`@${user.username}`);
      setCopied(user.id);
      setTimeout(() => setCopied((c) => (c === user.id ? null : c)), 1500);
    } catch {
      setError('Could not copy to clipboard');
    }
  }, []);

  const filters: { key: Filter; label: string; count: number; dot?: boolean }[] = [
    { key: 'all', label: 'All', count: counts.all },
    { key: 'online', label: 'Online', count: counts.online, dot: true },
    { key: 'recent', label: 'Recently Active', count: counts.recent },
  ];

  return (
    <div className="users-page">
      <header className="users-header">
        <div className="users-header-text">
          <h1 className="users-page-title">User Directory</h1>
          <p className="users-page-subtitle">Find and start a conversation with your team</p>
        </div>
        <div className="users-search-wrap">
          <svg className="users-search-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <circle cx="11" cy="11" r="8" />
            <path d="M21 21l-4.35-4.35" />
          </svg>
          <input
            type="search"
            className="users-search-input"
            placeholder="Search users by name or username..."
            aria-label="Search users"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>
      </header>

      <div className="users-toolbar">
        <div className="users-filters" role="tablist" aria-label="Filter users">
          {filters.map((f) => (
            <button
              key={f.key}
              role="tab"
              aria-selected={filter === f.key}
              className={`users-chip${filter === f.key ? ' users-chip--active' : ''}`}
              onClick={() => setFilter(f.key)}
            >
              {f.dot && <span className="users-chip-dot" aria-hidden="true" />}
              {f.label}
              <span className="users-chip-count">{f.count}</span>
            </button>
          ))}
        </div>

        <div className="users-toolbar-right">
          <label className="users-sort">
            <span className="sr-only">Sort users</span>
            <select value={sort} onChange={(e) => setSort(e.target.value as Sort)}>
              <option value="az">A–Z</option>
              <option value="za">Z–A</option>
              <option value="recent">Recently active</option>
            </select>
          </label>
          <div className="users-view-toggle" role="group" aria-label="View">
            <button
              className={view === 'grid' ? 'is-active' : ''}
              onClick={() => setView('grid')}
              aria-label="Grid view"
              aria-pressed={view === 'grid'}
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <rect x="3" y="3" width="7" height="7" rx="1" />
                <rect x="14" y="3" width="7" height="7" rx="1" />
                <rect x="3" y="14" width="7" height="7" rx="1" />
                <rect x="14" y="14" width="7" height="7" rx="1" />
              </svg>
            </button>
            <button
              className={view === 'list' ? 'is-active' : ''}
              onClick={() => setView('list')}
              aria-label="List view"
              aria-pressed={view === 'list'}
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <line x1="8" y1="6" x2="21" y2="6" />
                <line x1="8" y1="12" x2="21" y2="12" />
                <line x1="8" y1="18" x2="21" y2="18" />
                <circle cx="3.5" cy="6" r="1" />
                <circle cx="3.5" cy="12" r="1" />
                <circle cx="3.5" cy="18" r="1" />
              </svg>
            </button>
          </div>
        </div>
      </div>

      <div className="users-body">
        <div className="users-main">
          {error && <Alert type="error" message={error} onClose={() => setError(null)} />}

          {loading ? (
            <div className={`users-grid users-grid--${view}`}>
              {Array.from({ length: 6 }).map((_, i) => (
                <div key={i} className="user-card user-card-skeleton">
                  <Skeleton width={56} height={56} borderRadius="var(--radius-full)" />
                  <div className="user-card-skeleton-text">
                    <Skeleton width="60%" height={16} />
                    <Skeleton width="40%" height={12} />
                  </div>
                </div>
              ))}
            </div>
          ) : visible.length === 0 ? (
            <EmptyState
              title="No users found"
              description={query ? `No results for "${query}"` : 'No users match this filter'}
              icon={
                <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
                  <circle cx="9" cy="7" r="4" />
                  <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
                  <path d="M16 3.13a4 4 0 0 1 0 7.75" />
                </svg>
              }
            />
          ) : (
            <div className={`users-grid users-grid--${view}`}>
              {visible.map((user) => (
                <article
                  key={user.id}
                  className={`user-card${selectedId === user.id ? ' user-card--selected' : ''}`}
                >
                  <button
                    className="user-card-main"
                    onClick={() => setSelectedId(user.id)}
                    aria-label={`View ${user.displayName}`}
                  >
                    <Avatar name={user.displayName} src={user.avatarUrl} size="lg" online={user.online} />
                    <span className="user-card-info">
                      <span className="user-card-name">{user.displayName}</span>
                      <span className="user-card-username">@{user.username}</span>
                      <span className={`user-card-status${user.online ? ' user-card-status--online' : ''}`}>
                        <span className="user-card-status-dot" aria-hidden="true" />
                        {statusText(user)}
                      </span>
                    </span>
                  </button>

                  <div className="user-card-actions">
                    <button
                      className={`user-card-message-btn${selectedId === user.id ? ' user-card-message-btn--primary' : ''}`}
                      onClick={() => void handleMessage(user.id)}
                      disabled={creatingConv === user.id}
                    >
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                        <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
                      </svg>
                      {creatingConv === user.id ? 'Opening...' : 'Message'}
                    </button>

                    <div className="user-card-menu-wrap">
                      <button
                        className="user-card-more"
                        aria-label={`More options for ${user.displayName}`}
                        aria-haspopup="menu"
                        aria-expanded={menuId === user.id}
                        onClick={(e) => {
                          e.stopPropagation();
                          setMenuId((id) => (id === user.id ? null : user.id));
                        }}
                      >
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                          <circle cx="5" cy="12" r="1.8" />
                          <circle cx="12" cy="12" r="1.8" />
                          <circle cx="19" cy="12" r="1.8" />
                        </svg>
                      </button>
                      {menuId === user.id && (
                        <div className="user-card-menu" role="menu" onClick={(e) => e.stopPropagation()}>
                          <button
                            role="menuitem"
                            onClick={() => {
                              setSelectedId(user.id);
                              setMenuId(null);
                            }}
                          >
                            View profile
                          </button>
                          <button
                            role="menuitem"
                            onClick={() => {
                              setMenuId(null);
                              void handleMessage(user.id);
                            }}
                          >
                            Send message
                          </button>
                          <button
                            role="menuitem"
                            onClick={() => {
                              setMenuId(null);
                              void handleCopy(user);
                            }}
                          >
                            {copied === user.id ? 'Copied!' : 'Copy username'}
                          </button>
                        </div>
                      )}
                    </div>
                  </div>
                </article>
              ))}
            </div>
          )}
        </div>

        {selected && (
          <aside className="users-detail" aria-label={`${selected.displayName} profile`}>
            <button className="users-detail-close" onClick={() => setSelectedId(null)} aria-label="Close profile">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <line x1="18" y1="6" x2="6" y2="18" />
                <line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>

            <div className="users-detail-top">
              <Avatar name={selected.displayName} src={selected.avatarUrl} size="xl" online={selected.online} />
              <h2 className="users-detail-name">{selected.displayName}</h2>
              <span className="users-detail-username">@{selected.username}</span>
              <span className={`users-detail-badge${selected.online ? ' users-detail-badge--online' : ''}`}>
                <span className="user-card-status-dot" aria-hidden="true" />
                {statusText(selected)}
              </span>
            </div>

            <dl className="users-detail-info">
              <div>
                <dt>Username</dt>
                <dd>@{selected.username}</dd>
              </div>
              <div>
                <dt>Status</dt>
                <dd>{selected.online ? 'Online now' : 'Offline'}</dd>
              </div>
              <div>
                <dt>Last seen</dt>
                <dd>{selected.online ? 'Now' : lastSeenDate(selected)}</dd>
              </div>
            </dl>

            <div className="users-detail-actions">
              <button
                className="users-detail-primary"
                onClick={() => void handleMessage(selected.id)}
                disabled={creatingConv === selected.id}
              >
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <line x1="22" y1="2" x2="11" y2="13" />
                  <polygon points="22 2 15 22 11 13 2 9 22 2" />
                </svg>
                {creatingConv === selected.id ? 'Opening...' : 'Start Direct Conversation'}
              </button>
              <button className="users-detail-secondary" onClick={() => void handleCopy(selected)}>
                {copied === selected.id ? 'Copied!' : 'Copy username'}
              </button>
            </div>
          </aside>
        )}
      </div>
    </div>
  );
}
