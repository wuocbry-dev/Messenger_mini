import React, { useState } from 'react';
import type { Conversation } from '../../../../shared/types';
import { Avatar, Badge, EmptyState, Skeleton, SkeletonAvatar, IconButton } from '../../../../shared/components/ui';
import { NewConversationModal } from '../NewConversationModal';
import './Sidebar.css';

interface SidebarProps {
  currentUserId: number;
  conversations: Conversation[];
  activeConversationId: number | null;
  onSelectConversation: (conv: Conversation) => void;
  onlineUsers: Set<number>;
  loading: boolean;
  onRefresh: () => void;
  open?: boolean;
  onClose?: () => void;
}

export function Sidebar({
  conversations,
  activeConversationId,
  onSelectConversation,
  onlineUsers,
  loading,
  onRefresh: _onRefresh,
  open,
  onClose,
}: SidebarProps) {
  const [showNewConv, setShowNewConv] = useState(false);

  const formatTime = (iso: string) => {
    const d = new Date(iso);
    const now = new Date();
    const diffMs = now.getTime() - d.getTime();
    const diffDays = diffMs / (1000 * 60 * 60 * 24);
    if (diffDays < 1) return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    if (diffDays < 7) return d.toLocaleDateString([], { weekday: 'short' });
    return d.toLocaleDateString([], { month: 'short', day: 'numeric' });
  };

  const getConversationName = (conv: Conversation): string => {
    if (conv.title) return conv.title;
    if (conv.directWithUser) return conv.directWithUser.displayName;
    return conv.directKey ?? `Conversation ${conv.id}`;
  };

  const getOtherUser = (conv: Conversation) => {
    if (conv.directWithUser) return conv.directWithUser;
    return null;
  };

  return (
    <aside className={`sidebar${open ? ' sidebar--open' : ''}`} aria-label="Conversations">
      {open && (
        <div className="sidebar-overlay" onClick={onClose} aria-hidden="true" />
      )}
      <div className="sidebar-header">
        <h2 className="sidebar-title">Chats</h2>
        <div className="sidebar-header-actions">
          <IconButton
            label="New conversation"
            variant="ghost"
            size="sm"
            onClick={() => setShowNewConv(true)}
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M12 5v14M5 12h14" />
            </svg>
          </IconButton>
        </div>
      </div>

      <div className="sidebar-search">
        <div className="search-input-wrap">
          <svg className="search-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="11" cy="11" r="8" />
            <path d="M21 21l-4.35-4.35" />
          </svg>
          <input className="search-input" type="search" placeholder="Search conversations..." aria-label="Search conversations" />
        </div>
      </div>

      <div className="conversation-list" role="list">
        {loading ? (
          Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="conversation-item-skeleton" aria-hidden="true">
              <SkeletonAvatar size={40} />
              <div className="conversation-item-skeleton-text">
                <Skeleton width="60%" height={12} />
                <Skeleton width="80%" height={10} />
              </div>
            </div>
          ))
        ) : conversations.length === 0 ? (
          <EmptyState
            title="No conversations yet"
            description="Start a new conversation to begin chatting"
            action={
              <button className="btn-link" onClick={() => setShowNewConv(true)}>
                Start a chat
              </button>
            }
          />
        ) : (
          conversations.map((conv) => {
            const otherUser = getOtherUser(conv);
            const isActive = conv.id === activeConversationId;
            const isOnline = otherUser ? onlineUsers.has(otherUser.id) : false;
            const unread = conv.unreadCount ?? 0;

            return (
              <div
                key={conv.id}
                role="listitem"
                className={`conversation-item${isActive ? ' conversation-item--active' : ''}`}
                onClick={() => onSelectConversation(conv)}
                onKeyDown={(e) => e.key === 'Enter' && onSelectConversation(conv)}
                tabIndex={0}
                aria-selected={isActive}
              >
                <Avatar
                  name={getConversationName(conv)}
                  src={conv.avatarUrl}
                  size="md"
                  online={otherUser ? isOnline : undefined}
                />
                <div className="conversation-item-body">
                  <div className="conversation-item-row">
                    <span className="conversation-item-name truncate">
                      {getConversationName(conv)}
                    </span>
                    {conv.lastMessageAt && (
                      <span className="conversation-item-time">
                        {formatTime(conv.lastMessageAt)}
                      </span>
                    )}
                  </div>
                  <div className="conversation-item-row">
                    <span className="conversation-item-preview truncate">
                      {conv.lastMessage?.content ??
                        (conv.lastMessage?.type === 'IMAGE' ? '📷 Image' : '')}
                    </span>
                    {unread > 0 && (
                      <Badge variant="primary" size="sm">
                        {unread > 99 ? '99+' : unread}
                      </Badge>
                    )}
                  </div>
                </div>
              </div>
            );
          })
        )}
      </div>

      <NewConversationModal
        open={showNewConv}
        onClose={() => setShowNewConv(false)}
        onCreated={(conv) => {
          setShowNewConv(false);
          onSelectConversation(conv as unknown as Conversation);
        }}
      />
    </aside>
  );
}
