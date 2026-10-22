import React from 'react';
import type { Conversation } from '../../../../shared/types';
import { Avatar, IconButton } from '../../../../shared/components/ui';
import { formatLastSeen } from '../../../../shared/utils/lastSeen';
import { useNow } from '../../../../shared/hooks/useNow';
import './ChatHeader.css';

interface ChatHeaderProps {
  conversation: Conversation;
  currentUserId: number;
  online: boolean;
  /** Server-known last activity, shown while the peer is offline. */
  lastSeenAt?: string;
  typing: boolean;
  onCallClick: () => void;
  onVideoCallClick?: () => void;
  otherUserName?: string;
  otherUserAvatar?: string;
  otherUserId?: number;
  callInProgress?: boolean;
  /** Phone layout: returns to the conversation list. */
  onBack?: () => void;
  onMediaClick?: () => void;
  /** Group conversation: shows the member count and call buttons for the whole group. */
  isGroup?: boolean;
  memberCount?: number;
  /** Overrides the name derived from the conversation (e.g. member names of an untitled group). */
  displayTitle?: string;
}

export function ChatHeader({
  conversation,
  currentUserId,
  online,
  lastSeenAt,
  typing,
  onCallClick,
  onVideoCallClick,
  otherUserName,
  otherUserAvatar,
  otherUserId,
  callInProgress,
  onBack,
  onMediaClick,
  isGroup = false,
  memberCount = 0,
  displayTitle,
}: ChatHeaderProps) {
  const now = useNow(30_000); // keeps "Active 5 minutes ago" counting
  const name = displayTitle
    ?? conversation.title
    ?? otherUserName
    ?? conversation.directWithUser?.displayName
    ?? 'Chat';

  return (
    <header className={`chat-header${onBack ? ' chat-header--mobile' : ''}`}>
      <div className="chat-header-info">
        {onBack && (
          <button type="button" className="chat-header-back-btn" onClick={onBack} aria-label="Back to chats">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.25" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <polyline points="15 18 9 12 15 6" />
            </svg>
          </button>
        )}
        <Avatar
          name={name}
          src={otherUserAvatar ?? conversation.avatarUrl}
          size="lg"
          online={otherUserId != null ? online : undefined}
        />
        <div className="chat-header-text">
          <h3 className="chat-header-name">{name}</h3>
          <span
            className={`chat-header-status${typing ? ' chat-header-status--typing' : ''}${
              otherUserId != null && online && !typing ? ' chat-header-status--online' : ''
            }`}
          >
            {typing
              ? 'typing...'
              : isGroup
              ? `${memberCount} members`
              : otherUserId != null
              ? (online ? 'Online' : formatLastSeen(lastSeenAt, now))
              : `${conversation.type.toLowerCase()} conversation`}
          </span>
        </div>
      </div>
      <div className="chat-header-actions">
        {(otherUserId != null || isGroup) && !callInProgress && (
          <IconButton
            label="Voice call"
            variant="ghost"
            size="md"
            onClick={onCallClick}
          >
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07A19.5 19.5 0 0 1 4.69 12 19.79 19.79 0 0 1 1.61 3.38 2 2 0 0 1 3.6 1.22h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z" />
            </svg>
          </IconButton>
        )}
        {(otherUserId != null || isGroup) && !callInProgress && onVideoCallClick && (
          <IconButton
            label="Video call"
            variant="ghost"
            size="md"
            onClick={onVideoCallClick}
          >
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <polygon points="23 7 16 12 23 17 23 7" />
              <rect x="1" y="5" width="15" height="14" rx="2" ry="2" />
            </svg>
          </IconButton>
        )}
        {onMediaClick && (
          <>
            <span className="chat-header-divider" aria-hidden="true" />
            <IconButton
              label="More options"
              variant="ghost"
              size="md"
              onClick={onMediaClick}
            >
              <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor" stroke="none">
                <circle cx="5" cy="12" r="1.8" />
                <circle cx="12" cy="12" r="1.8" />
                <circle cx="19" cy="12" r="1.8" />
              </svg>
            </IconButton>
          </>
        )}
      </div>
    </header>
  );
}
