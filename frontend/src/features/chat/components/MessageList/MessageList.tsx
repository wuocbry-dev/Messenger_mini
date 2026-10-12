import React, { useEffect, useRef } from 'react';
import type { Message, MessageReceipt } from '../../../../shared/types';
import { Avatar, EmptyState, Skeleton } from '../../../../shared/components/ui';
import { messageApi } from '../../api';
import './MessageList.css';

interface MessageListProps {
  messages: Message[];
  currentUserId: number;
  typingUser?: string;
  loading: boolean;
}

function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function formatDate(iso: string): string {
  const d = new Date(iso);
  const today = new Date();
  const yesterday = new Date(today);
  yesterday.setDate(yesterday.getDate() - 1);

  if (d.toDateString() === today.toDateString()) return 'Today';
  if (d.toDateString() === yesterday.toDateString()) return 'Yesterday';
  return d.toLocaleDateString([], { month: 'long', day: 'numeric', year: 'numeric' });
}

function shouldShowDate(prev: Message | null, curr: Message): boolean {
  if (!prev) return true;
  return new Date(prev.createdAt).toDateString() !== new Date(curr.createdAt).toDateString();
}

function ReceiptIcon({ receipt }: { receipt: MessageReceipt }) {
  if (receipt.state === 'READ') {
    return (
      <span className="receipt-icon receipt-read" title="Read">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
          <polyline points="20 6 9 17 4 12" />
          <polyline points="9 6 20 17 4 12" />
        </svg>
      </span>
    );
  }
  return (
    <span className="receipt-icon receipt-delivered" title="Delivered">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
        <polyline points="20 6 9 17 4 12" />
      </svg>
    </span>
  );
}

export function MessageList({ messages, currentUserId, typingUser, loading }: MessageListProps) {
  const bottomRef = useRef<HTMLDivElement>(null);
  const prevMessage = useRef<Message | null>(null);

  const openAttachment = async (fileId: number) => {
    const file = await messageApi.getFile(fileId);
    window.open(file.downloadUrl, '_blank', 'noopener,noreferrer');
  };

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, typingUser]);

  if (loading) {
    return (
      <div className="message-list">
        {Array.from({ length: 5 }).map((_, i) => (
          <div key={i} className="message-skeleton">
            <Skeleton width={32} height={32} borderRadius="50%" />
            <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 6 }}>
              <Skeleton width="30%" height={10} />
              <Skeleton width="60%" height={14} />
            </div>
          </div>
        ))}
      </div>
    );
  }

  if (messages.length === 0) {
    return (
      <div className="message-list">
        <EmptyState
          title="No messages yet"
          description="Send a message to start the conversation"
        />
      </div>
    );
  }

  return (
    <div className="message-list" role="log" aria-live="polite" aria-label="Messages">
      {messages.map((msg, i) => {
        const isOwn = msg.senderId === currentUserId;
        const showDate = shouldShowDate(prevMessage.current, msg);
        const senderName = isOwn ? 'You' : msg.senderDisplayName;
        const attachment = msg.attachments?.[0];
        prevMessage.current = msg;

        return (
          <React.Fragment key={msg.id}>
            {showDate && (
              <div className="date-divider">
                <span className="date-divider-text">{formatDate(msg.createdAt)}</span>
              </div>
            )}
            <div className={`message-row${isOwn ? ' message-row--own' : ''}`}>
              {!isOwn && (
                <Avatar
                  name={msg.senderDisplayName}
                  src={msg.senderAvatarUrl}
                  size="sm"
                />
              )}
              <div className={`message-bubble${isOwn ? ' message-bubble--own' : ''}`}>
                {!isOwn && (
                  <span className="message-sender">{msg.senderDisplayName}</span>
                )}
                {msg.type === 'TEXT' && (
                  <p className="message-content">{msg.content}</p>
                )}
                {msg.type === 'IMAGE' && (
                  <div className="message-attachment">
                    {msg.downloadUrl ? (
                      <img
                        src={msg.downloadUrl}
                        alt={msg.fileName ?? attachment?.originalName ?? 'image'}
                        className="message-image"
                        loading="lazy"
                      />
                    ) : attachment ? (
                      <button
                        type="button"
                        className="message-file"
                        onClick={() => void openAttachment(attachment.fileId)}
                      >
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                          <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
                          <circle cx="8.5" cy="8.5" r="1.5" />
                          <path d="M21 15l-5-5L5 21" />
                        </svg>
                        <span className="message-file-link">{attachment.originalName ?? 'Open image'}</span>
                      </button>
                    ) : null}
                    {msg.content && <p className="message-content">{msg.content}</p>}
                  </div>
                )}
                {msg.type === 'FILE' && (
                  <div className="message-file">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z" />
                      <polyline points="13 2 13 9 20 9" />
                    </svg>
                    {msg.downloadUrl ? (
                      <a href={msg.downloadUrl} target="_blank" rel="noopener noreferrer" className="message-file-link">
                        {msg.fileName ?? attachment?.originalName ?? 'Download file'}
                      </a>
                    ) : attachment ? (
                      <button
                        type="button"
                        className="message-file-link message-file-button"
                        onClick={() => void openAttachment(attachment.fileId)}
                      >
                        {attachment.originalName ?? 'Download file'}
                      </button>
                    ) : null}
                    {(msg.fileSize ?? attachment?.sizeBytes) && (
                      <span className="message-file-size">
                        {(msg.fileSize ?? attachment!.sizeBytes!) > 1024 * 1024
                          ? `${((msg.fileSize ?? attachment!.sizeBytes!) / 1024 / 1024).toFixed(1)} MB`
                          : `${Math.round((msg.fileSize ?? attachment!.sizeBytes!) / 1024)} KB`}
                      </span>
                    )}
                  </div>
                )}
                <div className="message-meta">
                  <span className="message-time">{formatTime(msg.createdAt)}</span>
                  {isOwn && msg.receipts?.length > 0 && (
                    <ReceiptIcon receipt={msg.receipts[msg.receipts.length - 1]} />
                  )}
                </div>
              </div>
            </div>
          </React.Fragment>
        );
      })}

      {typingUser && (
        <div className="typing-indicator" aria-live="polite">
          <Avatar name={typingUser} size="sm" />
          <div className="typing-dots">
            <span /><span /><span />
          </div>
          <span className="typing-label">{typingUser} is typing</span>
        </div>
      )}

      <div ref={bottomRef} />
    </div>
  );
}
