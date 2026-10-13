import React, { useState, useCallback, useEffect } from 'react';
import { Avatar } from '../../../shared/components/ui/Avatar';
import { EmptyState } from '../../../shared/components/ui/EmptyState';
import { Skeleton } from '../../../shared/components/ui/Skeleton';
import { userApi } from '../api/userApi';
import { conversationApi } from '../api/conversationApi';
import type { UserSummary } from '../../../shared/types';
import './UsersPage.css';

export function UsersPage() {
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [creatingConv, setCreatingConv] = useState<number | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    userApi
      .list()
      .then((data) => {
        if (!cancelled) setUsers(data);
      })
      .catch(console.error)
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const handleSearch = useCallback(async (query: string) => {
    setSearchQuery(query);
    if (query.length < 2) {
      userApi.search('').then(setUsers).catch(console.error);
      return;
    }
    setLoading(true);
    try {
      const results = await userApi.search(query);
      setUsers(results);
    } catch (err) {
      console.error('Search failed:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  const handleMessage = useCallback(async (userId: number) => {
    setCreatingConv(userId);
    try {
      await conversationApi.create(userId);
      // Navigate to chat
      window.location.href = '/';
    } catch (err) {
      console.error('Failed to start conversation:', err);
    } finally {
      setCreatingConv(null);
    }
  }, []);

  const filteredUsers = users.filter((user) =>
    user.displayName.toLowerCase().includes(searchQuery.toLowerCase()) ||
    user.username.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <div className="users-page">
      <div className="users-page-header">
        <h1 className="users-page-title">Users</h1>
        <p className="users-page-subtitle">Find and connect with people</p>
      </div>

      <div className="users-search">
        <div className="users-search-wrap">
          <svg className="users-search-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="11" cy="11" r="8" />
            <path d="M21 21l-4.35-4.35" />
          </svg>
          <input
            type="search"
            className="users-search-input"
            placeholder="Search by name or username..."
            value={searchQuery}
            onChange={(e) => handleSearch(e.target.value)}
          />
        </div>
      </div>

      <div className="users-list">
        {loading ? (
          Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="user-card-skeleton">
              <Skeleton width={48} height={48} borderRadius="var(--radius-full)" />
              <div className="user-card-skeleton-text">
                <Skeleton width="60%" height={16} />
                <Skeleton width="40%" height={12} />
              </div>
            </div>
          ))
        ) : filteredUsers.length === 0 ? (
          <EmptyState
            title="No users found"
            description={searchQuery ? `No results for "${searchQuery}"` : 'No users available'}
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
          filteredUsers.map((user) => (
            <div key={user.id} className="user-card">
              <Avatar
                name={user.displayName}
                src={user.avatarUrl}
                size="md"
              />
              <div className="user-card-info">
                <span className="user-card-name">{user.displayName}</span>
                <span className="user-card-username">@{user.username}</span>
              </div>
              <button
                className="user-card-message-btn"
                onClick={() => handleMessage(user.id)}
                disabled={creatingConv === user.id}
              >
                {creatingConv === user.id ? (
                  <svg className="animate-spin" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <circle cx="12" cy="12" r="10" strokeOpacity="0.25" />
                    <path d="M12 2a10 10 0 0 1 10 10" />
                  </svg>
                ) : (
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
                  </svg>
                )}
                Message
              </button>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
