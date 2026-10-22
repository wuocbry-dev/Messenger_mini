import React, { useState } from 'react';
import type { Conversation } from '../../../../shared/types';
import { Avatar, Badge, EmptyState, Skeleton, SkeletonAvatar, IconButton } from '../../../../shared/components/ui';
import { NewConversationModal } from '../NewConversationModal';
import { NewGroupModal } from '../NewGroupModal';
import { getConversationTitle } from '../../utils/conversationName';
import './Sidebar.css';

interface SidebarProps {
  currentUserId: number;
  conversations: Conversation[];
  activeConversationId: number | null;
  onSelectConversation: (conv: Conversation) => void;
  onlineUsers: Set<number>;
  loading: boolean;
  onRefresh: () => void;
}

export function Sidebar({
  currentUserId,
  conversations,
  activeConversationId,
  onSelectConversation,
  onlineUsers,
  loading,
  onRefresh,
}: SidebarProps) {
  const [showNewConv, setShowNewConv] = useState(false);
  const [showNewGroup, setShowNewGroup] = useState(false);
  const [query, setQuery] = useState('');

  const getConversationName = (conv: Conversation): string => getConversationTitle(conv, currentUserId);

  const getOtherUser = (conv: Conversation) => {
    if (conv.directWithUser) return conv.directWithUser;
    return null;
  };

  const needle = query.trim().toLowerCase();
  const visible = needle
    ? conversations.filter((conv) => {
        const other = getOtherUser(conv);
        return [getConversationName(conv), other?.username, conv.lastMessage?.content]
          .some((text) => text?.toLowerCase().includes(needle));
      })
    : conversations;

  return (
    <aside className="sidebar" aria-label="Conversations">
      {/* Phone layout only: the desktop left card already carries the app title. */}
      <h2 className="sidebar-mobile-title">Chats</h2>
      <div className="sidebar-search">
        <div className="search-input-wrap">
          <svg className="search-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="11" cy="11" r="8" />
            <path d="M21 21l-4.35-4.35" />
          </svg>
          <input
            className="search-input"
            type="search"
            placeholder="Search conversations..."
            aria-label="Search conversations"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>
        <IconButton
          label="New conversation"
          variant="ghost"
          size="md"
          onClick={() => setShowNewConv(true)}
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M12 5v14M5 12h14" />
          </svg>
        </IconButton>
        <IconButton
          label="New group"
          variant="ghost"
          size="md"
          onClick={() => setShowNewGroup(true)}
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
            <circle cx="9" cy="7" r="4" />
            <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
            <path d="M16 3.13a4 4 0 0 1 0 7.75" />
          </svg>
        </IconButton>
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
        ) : visible.length === 0 ? (
          <EmptyState title="No matches" description={`No conversations match "${query.trim()}"`} />
        ) : (
          visible.map((conv) => {
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
                  src={otherUser?.avatarUrl ?? conv.avatarUrl}
                  size="lg"
                  online={otherUser ? isOnline : undefined}
                />
                <div className="conversation-item-body">
                  <span className="conversation-item-name truncate">
                    {getConversationName(conv)}
                  </span>
                  {conv.lastMessage && (
                    <span className="conversation-item-preview truncate">
                      {conv.lastMessage.content ??
                        (conv.lastMessage.type === 'IMAGE' ? '📷 Image' : 'Attachment')}
                    </span>
                  )}
                </div>
                {unread > 0 && (
                  <Badge variant="primary" size="sm">
                    {unread > 99 ? '99+' : unread}
                  </Badge>
                )}
              </div>
            );
          })
        )}
      </div>

      <NewGroupModal
        open={showNewGroup}
        onClose={() => setShowNewGroup(false)}
        onCreated={(conv) => {
          setShowNewGroup(false);
          onRefresh();
          onSelectConversation(conv as unknown as Conversation);
        }}
      />
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
