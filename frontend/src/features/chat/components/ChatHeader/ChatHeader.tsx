import React from 'react';
import type { Conversation } from '../../../../shared/types';
import { Avatar, IconButton } from '../../../../shared/components/ui';
import './ChatHeader.css';

interface ChatHeaderProps {
  conversation: Conversation;
  currentUserId: number;
  online: boolean;
  typing: boolean;
  onCallClick: () => void;
  otherUserName?: string;
  otherUserAvatar?: string;
  otherUserId?: number;
  callInProgress?: boolean;
  onMenuClick?: () => void;
}

export function ChatHeader({
  conversation,
  currentUserId,
  online,
  typing,
  onCallClick,
  otherUserName,
  otherUserAvatar,
  otherUserId,
  callInProgress,
  onMenuClick,
}: ChatHeaderProps) {
  const name = conversation.title
    ?? otherUserName
    ?? conversation.directWithUser?.displayName
    ?? 'Chat';

  return (
    <header className="chat-header">
      <div className="chat-header-info">
        <Avatar
          name={name}
          src={otherUserAvatar ?? conversation.avatarUrl}
          size="md"
          online={otherUserId != null ? online : undefined}
        />
        <div className="chat-header-text">
          <h3 className="chat-header-name">{name}</h3>
          <span className={`chat-header-status${typing ? ' chat-header-status--typing' : ''}`}>
            {typing
              ? 'typing...'
              : otherUserId != null
              ? (online ? 'online' : 'offline')
              : `${conversation.type.toLowerCase()} conversation`}
          </span>
        </div>
      </div>
      <div className="chat-header-actions">
        <button
          className="chat-header-menu-btn"
          onClick={onMenuClick}
          aria-label="Open conversations"
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <line x1="3" y1="6" x2="21" y2="6" />
            <line x1="3" y1="12" x2="21" y2="12" />
            <line x1="3" y1="18" x2="21" y2="18" />
          </svg>
        </button>
        {otherUserId != null && !callInProgress && (
          <IconButton
            label="Voice call"
            variant="ghost"
            size="md"
            onClick={onCallClick}
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07A19.5 19.5 0 0 1 4.69 12 19.79 19.79 0 0 1 1.61 3.38 2 2 0 0 1 3.6 1.22h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z" />
            </svg>
          </IconButton>
        )}
      </div>
    </header>
  );
}
