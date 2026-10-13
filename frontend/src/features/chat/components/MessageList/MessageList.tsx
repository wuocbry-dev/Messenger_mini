import React, { useEffect, useRef, useState } from 'react';
import type { Message, MessageAttachment, MessageReceipt } from '../../../../shared/types';
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

function isImageAttachment(attachment: MessageAttachment): boolean {
  return attachment.attachmentType === 'IMAGE' || attachment.mimeType?.startsWith('image/') === true;
}

function formatFileSize(bytes?: number): string | null {
  if (!bytes) return null;
  if (bytes > 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  return `${Math.max(1, Math.round(bytes / 1024))} KB`;
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
  const [fileUrls, setFileUrls] = useState<Record<number, string>>({});

  const openAttachment = async (fileId: number) => {
    const file = await messageApi.getFile(fileId);
    window.open(file.downloadUrl, '_blank', 'noopener,noreferrer');
  };

  useEffect(() => {
    const imageFileIds = messages
      .flatMap((msg) => msg.attachments ?? [])
      .filter(isImageAttachment)
      .map((attachment) => attachment.fileId)
      .filter((fileId) => !fileUrls[fileId]);

    const missingFileIds = Array.from(new Set(imageFileIds));
    if (missingFileIds.length === 0) return;

    let cancelled = false;
    void Promise.all(
      missingFileIds.map(async (fileId) => {
        try {
          const file = await messageApi.getFile(fileId);
          return [fileId, file.downloadUrl] as const;
        } catch {
          return null;
        }
      })
    ).then((entries) => {
      if (cancelled) return;
      const nextEntries = entries.filter((entry): entry is readonly [number, string] => entry != null);
      if (nextEntries.length === 0) return;
      setFileUrls((prev) => {
        const next = { ...prev };
        nextEntries.forEach(([fileId, url]) => {
          next[fileId] = url;
        });
        return next;
      });
    });

    return () => {
      cancelled = true;
    };
  }, [messages, fileUrls]);

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
        const showDate = shouldShowDate(messages[i - 1] ?? null, msg);
        const attachments = msg.attachments ?? [];
        const imageAttachments = attachments.filter(isImageAttachment);
        const fileAttachments = attachments.filter((attachment) => !isImageAttachment(attachment));
        const hasAttachments = imageAttachments.length > 0 || fileAttachments.length > 0;

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
              <div className={`message-bubble${isOwn ? ' message-bubble--own' : ''}${hasAttachments ? ' message-bubble--media' : ''}`}>
                {!isOwn && (
                  <span className="message-sender">{msg.senderDisplayName}</span>
                )}
                {msg.type === 'TEXT' && (
                  <p className="message-content">{msg.content}</p>
                )}
                {imageAttachments.length > 0 && (
                  <div className="message-attachment">
                    <div className={`message-image-grid${imageAttachments.length === 1 ? ' message-image-grid--single' : ''}`}>
                      {imageAttachments.map((attachment) => {
                        const url = fileUrls[attachment.fileId] ?? msg.downloadUrl;
                        return url ? (
                          <button
                            key={attachment.id ?? attachment.fileId}
                            type="button"
                            className="message-image-button"
                            onClick={() => void openAttachment(attachment.fileId)}
                            title={attachment.originalName ?? 'Open image'}
                          >
                            <img
                              src={url}
                              alt={attachment.originalName ?? msg.fileName ?? 'image'}
                              className="message-image"
                              loading="lazy"
                            />
                          </button>
                        ) : (
                          <button
                            key={attachment.id ?? attachment.fileId}
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
                        );
                      })}
                    </div>
                    {msg.content && <p className="message-content">{msg.content}</p>}
                  </div>
                )}
                {fileAttachments.length > 0 && (
                  <div className="message-file-list">
                    {fileAttachments.map((attachment) => (
                      <button
                        key={attachment.id ?? attachment.fileId}
                        type="button"
                        className="message-file"
                        onClick={() => void openAttachment(attachment.fileId)}
                      >
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                          <path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z" />
                          <polyline points="13 2 13 9 20 9" />
                        </svg>
                        <span className="message-file-link">{attachment.originalName ?? msg.fileName ?? 'Download file'}</span>
                        {formatFileSize(attachment.sizeBytes ?? msg.fileSize) && (
                          <span className="message-file-size">{formatFileSize(attachment.sizeBytes ?? msg.fileSize)}</span>
                        )}
                      </button>
                    ))}
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
