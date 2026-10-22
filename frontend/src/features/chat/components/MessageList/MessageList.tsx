import React, { useEffect, useMemo, useRef, useState } from 'react';
import type { Message, MessageAttachment, MessageReceipt } from '../../../../shared/types';
import { Avatar, EmptyState, Skeleton } from '../../../../shared/components/ui';
import { messageApi } from '../../api';
import { ImageLightbox, type LightboxImage } from '../ImageLightbox';
import { openResolvedUrl } from '../../../../shared/utils/openResolvedUrl';
import { parseUtc } from '../../../../shared/utils/datetime';
import { describeCallMessage } from '../../utils/callMessage';
import './MessageList.css';

interface MessageListProps {
  messages: Message[];
  currentUserId: number;
  typingUser?: string;
  typingUserAvatar?: string;
  /** Several people are typing ("are typing" instead of "is typing"). */
  typingPlural?: boolean;
  loading: boolean;
  /** Resolves a user's avatar URL; the message payload does not carry one. */
  getUserAvatar?: (userId: number) => string | undefined;
  /** Starts a new call of the given type from a call entry in the timeline. */
  onCallBack?: (type: 'VOICE' | 'VIDEO') => void;
  /** A call is already running, so call entries cannot start another. */
  callDisabled?: boolean;
}

/** Message times are UTC without a zone suffix; show them in local time. */
function toDate(iso: string): Date {
  return new Date(parseUtc(iso) ?? Date.now());
}

function formatTime(iso: string): string {
  return toDate(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function formatDate(iso: string): string {
  const d = toDate(iso);
  const today = new Date();
  const yesterday = new Date(today);
  yesterday.setDate(yesterday.getDate() - 1);

  if (d.toDateString() === today.toDateString()) return 'Today';
  if (d.toDateString() === yesterday.toDateString()) return 'Yesterday';
  return d.toLocaleDateString([], { month: 'long', day: 'numeric', year: 'numeric' });
}

function shouldShowDate(prev: Message | null, curr: Message): boolean {
  if (!prev) return true;
  return toDate(prev.createdAt).toDateString() !== toDate(curr.createdAt).toDateString();
}

function isImageAttachment(attachment: MessageAttachment): boolean {
  return attachment.attachmentType === 'IMAGE' || attachment.mimeType?.startsWith('image/') === true;
}

/** Images sent within this window by the same person are shown as one group. */
const GROUP_WINDOW_MS = 2 * 60 * 1000;

interface MessageGroup {
  messages: Message[];
}

function isImageOnly(msg: Message): boolean {
  const atts = msg.attachments ?? [];
  return atts.length > 0 && !msg.content?.trim() && atts.every(isImageAttachment);
}

function groupMessages(messages: Message[]): MessageGroup[] {
  const groups: MessageGroup[] = [];
  for (const msg of messages) {
    const last = groups[groups.length - 1];
    const prev = last?.messages[last.messages.length - 1];
    if (
      prev &&
      isImageOnly(msg) &&
      isImageOnly(prev) &&
      prev.senderId === msg.senderId &&
      toDate(prev.createdAt).toDateString() === toDate(msg.createdAt).toDateString() &&
      toDate(msg.createdAt).getTime() - toDate(prev.createdAt).getTime() <= GROUP_WINDOW_MS
    ) {
      last.messages.push(msg);
    } else {
      groups.push({ messages: [msg] });
    }
  }
  return groups;
}

function formatFileSize(bytes?: number): string | null {
  if (!bytes) return null;
  if (bytes > 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  return `${Math.max(1, Math.round(bytes / 1024))} KB`;
}

/** One tick state for the message: read only when every recipient has read it. */
function summarizeReceipts(receipts: MessageReceipt[]): MessageReceipt {
  return receipts.find((r) => r.state !== 'READ') ?? receipts[receipts.length - 1];
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

export function MessageList({
  messages,
  currentUserId,
  typingUser,
  typingUserAvatar,
  typingPlural,
  loading,
  getUserAvatar,
  onCallBack,
  callDisabled,
}: MessageListProps) {
  const bottomRef = useRef<HTMLDivElement>(null);
  const [fileUrls, setFileUrls] = useState<Record<number, string>>({});
  const [lightbox, setLightbox] = useState<{ images: LightboxImage[]; index: number } | null>(null);
  const groups = useMemo(() => groupMessages(messages), [messages]);

  const openAttachment = (fileId: number) =>
    openResolvedUrl(async () => (await messageApi.getFile(fileId)).downloadUrl);

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
      {groups.map((group, i) => {
        const first = group.messages[0];
        const msg = group.messages[group.messages.length - 1]; // time/receipt of the latest
        const prevGroup = groups[i - 1];
        const isOwn = msg.senderId === currentUserId;
        const showDate = shouldShowDate(
          prevGroup ? prevGroup.messages[prevGroup.messages.length - 1] : null,
          first
        );
        const attachments = group.messages.flatMap((m) => m.attachments ?? []);
        const lightboxImages: LightboxImage[] = attachments
          .filter(isImageAttachment)
          .map((a) => ({ fileId: a.fileId, name: a.originalName }));
        const imageAttachments = attachments.filter(isImageAttachment);
        const fileAttachments = attachments.filter((attachment) => !isImageAttachment(attachment));
        const hasAttachments = imageAttachments.length > 0 || fileAttachments.length > 0;
        const call = msg.type === 'CALL' && msg.call ? describeCallMessage(msg.call, currentUserId) : null;

        return (
          <React.Fragment key={first.id}>
            {showDate && (
              <div className="date-divider">
                <span className="date-divider-text">{formatDate(first.createdAt)}</span>
              </div>
            )}
            <div className={`message-row${isOwn ? ' message-row--own' : ''}`}>
              {!isOwn && (
                <Avatar
                  name={msg.senderDisplayName}
                  src={msg.senderAvatarUrl ?? getUserAvatar?.(msg.senderId)}
                  size="sm"
                />
              )}
              <div className={`message-bubble${isOwn ? ' message-bubble--own' : ''}${hasAttachments ? ' message-bubble--media' : ''}${call ? ' message-bubble--call' : ''}`}>
                {!isOwn && !call && (
                  <span className="message-sender">{msg.senderDisplayName}</span>
                )}
                {msg.type === 'TEXT' && (
                  <p className="message-content">{msg.content}</p>
                )}
                {call && msg.call && (
                  <div className={`message-call${call.missed ? ' message-call--missed' : ''}`}>
                    <div className="message-call-main">
                      <span className="message-call-icon" aria-hidden="true">
                        {call.video ? (
                          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                            <polygon points="23 7 16 12 23 17 23 7" />
                            <rect x="1" y="5" width="15" height="14" rx="2" ry="2" />
                          </svg>
                        ) : (
                          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                            <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07A19.5 19.5 0 0 1 4.69 12 19.79 19.79 0 0 1 1.61 3.38 2 2 0 0 1 3.6 1.22h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z" />
                          </svg>
                        )}
                      </span>
                      <span className="message-call-text">
                        <span className="message-call-title">{call.title}</span>
                        {call.detail && <span className="message-call-detail">{call.detail}</span>}
                      </span>
                    </div>
                    {onCallBack && (
                      <button
                        type="button"
                        className="message-call-action"
                        onClick={() => onCallBack(msg.call!.callType)}
                        disabled={callDisabled}
                      >
                        {call.missed ? 'Call back' : 'Call again'}
                      </button>
                    )}
                  </div>
                )}
                {imageAttachments.length > 1 && (
                  <div className="message-attachment">
                    <span className="message-stack-label">
                      {isOwn ? 'You' : msg.senderDisplayName} sent {imageAttachments.length} photos
                    </span>
                    <button
                      type="button"
                      className="message-stack"
                      onClick={() => setLightbox({ images: lightboxImages, index: 0 })}
                      aria-label={`Open ${imageAttachments.length} photos`}
                    >
                      {imageAttachments.slice(0, 3).map((attachment, layer) => (
                        <span key={attachment.id ?? attachment.fileId} className={`message-stack-card message-stack-card--${layer}`}>
                          {fileUrls[attachment.fileId] ? (
                            <img src={fileUrls[attachment.fileId]} alt="" />
                          ) : null}
                        </span>
                      ))}
                      {imageAttachments.length > 3 && (
                        <span className="message-stack-more">+{imageAttachments.length - 3}</span>
                      )}
                    </button>
                  </div>
                )}
                {imageAttachments.length === 1 && (
                  <div className="message-attachment">
                    <div className={`message-image-grid${imageAttachments.length === 1 ? ' message-image-grid--single' : ''}`}>
                      {imageAttachments.map((attachment) => {
                        const url = fileUrls[attachment.fileId] ?? msg.downloadUrl;
                        return url ? (
                          <button
                            key={attachment.id ?? attachment.fileId}
                            type="button"
                            className="message-image-button"
                            onClick={() =>
                              setLightbox({
                                images: lightboxImages,
                                index: Math.max(0, lightboxImages.findIndex((x) => x.fileId === attachment.fileId)),
                              })
                            }
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
                  {isOwn && !call && msg.receipts?.length > 0 && (
                    <ReceiptIcon receipt={summarizeReceipts(msg.receipts)} />
                  )}
                </div>
              </div>
            </div>
          </React.Fragment>
        );
      })}

      {typingUser && (
        <div className="typing-indicator" aria-live="polite">
          <Avatar name={typingUser} src={typingUserAvatar} size="sm" />
          <div className="typing-dots">
            <span /><span /><span />
          </div>
          <span className="typing-label">{typingUser} {typingPlural ? 'are' : 'is'} typing</span>
        </div>
      )}

      <div ref={bottomRef} />

      {lightbox && (
        <ImageLightbox
          images={lightbox.images}
          urls={fileUrls}
          startIndex={lightbox.index}
          onClose={() => setLightbox(null)}
        />
      )}
    </div>
  );
}
