import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { EmptyState } from '../../../../shared/components/ui/EmptyState';
import { Skeleton } from '../../../../shared/components/ui/Skeleton';
import type { Conversation, Message, MessageAttachment } from '../../../../shared/types';
import { messageApi } from '../../api/messageApi';
import { ImageLightbox } from '../ImageLightbox';
import './SharedMediaDrawer.css';

interface SharedMediaDrawerProps {
  conversation: Conversation | null;
  open: boolean;
  onClose: () => void;
}

type Tab = 'media' | 'files';

export function SharedMediaDrawer({ conversation, open, onClose }: SharedMediaDrawerProps) {
  const [activeTab, setActiveTab] = useState<Tab>('media');
  const [messages, setMessages] = useState<Message[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!open || !conversation) {
      setMessages([]);
      return;
    }

    let cancelled = false;
    setLoading(true);

    messageApi
      .list(conversation.id, { page: 0, size: 100 })
      .then((res) => {
        if (cancelled) return;
        const msgs = res.messages ?? res.content ?? [];
        setMessages(msgs); // API order is newest first
      })
      .catch(console.error)
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [open, conversation?.id]);

  // Flatten attachments (newest first); the message payload carries file ids,
  // not URLs, so presigned URLs are fetched on demand.
  const attachments = useMemo(
    () =>
      messages.flatMap((m) =>
        (m.attachments ?? []).map((att) => ({ att, createdAt: m.createdAt, messageId: m.id }))
      ),
    [messages]
  );
  const images = useMemo(() => attachments.filter(({ att }) => isImage(att)), [attachments]);
  const files = useMemo(() => attachments.filter(({ att }) => !isImage(att)), [attachments]);

  const [urls, setUrls] = useState<Record<number, string>>({});
  const [viewer, setViewer] = useState<number | null>(null);
  useEffect(() => {
    const missing = Array.from(new Set(images.map(({ att }) => att.fileId))).filter((id) => !urls[id]);
    if (!open || missing.length === 0) return;
    let cancelled = false;
    void Promise.all(
      missing.map(async (id) => {
        try {
          return [id, (await messageApi.getFile(id)).downloadUrl] as const;
        } catch {
          return null;
        }
      })
    ).then((entries) => {
      if (cancelled) return;
      const ok = entries.filter((e): e is readonly [number, string] => e != null);
      if (ok.length) setUrls((prev) => ({ ...prev, ...Object.fromEntries(ok) }));
    });
    return () => {
      cancelled = true;
    };
  }, [open, images, urls]);

  const openFile = useCallback(async (fileId: number) => {
    try {
      const file = await messageApi.getFile(fileId);
      window.open(file.downloadUrl, '_blank', 'noopener,noreferrer');
    } catch (err) {
      console.error('Open failed:', err);
    }
  }, []);

  // Always mounted so it can slide in and out; hidden from assistive tech
  // and the tab order while closed.
  return (
    <div
      className={`shared-media-drawer${open ? ' shared-media-drawer--open' : ''}`}
      aria-hidden={!open}
    >
    <div className="shared-media-panel">
      <div className="shared-media-header">
        <h3 className="shared-media-title">Shared</h3>
        <button className="shared-media-close" onClick={onClose} aria-label="Close">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <line x1="18" y1="6" x2="6" y2="18" />
            <line x1="6" y1="6" x2="18" y2="18" />
          </svg>
        </button>
      </div>

      <div className="shared-media-tabs">
        <button
          className={`shared-media-tab${activeTab === 'media' ? ' shared-media-tab--active' : ''}`}
          onClick={() => setActiveTab('media')}
        >
          Media <span className="shared-media-count">{images.length}</span>
        </button>
        <button
          className={`shared-media-tab${activeTab === 'files' ? ' shared-media-tab--active' : ''}`}
          onClick={() => setActiveTab('files')}
        >
          Files <span className="shared-media-count">{files.length}</span>
        </button>
      </div>

      <div className="shared-media-content">
        {loading ? (
          <div className="shared-media-loading">
            {Array.from({ length: 6 }).map((_, i) => (
              <Skeleton key={i} width="100%" height={120} borderRadius="var(--radius-lg)" />
            ))}
          </div>
        ) : activeTab === 'media' ? (
          images.length > 0 ? (
            <div className="shared-media-grid">
              {images.map(({ att, messageId }, idx) => (
                <button
                  key={`${messageId}-${att.id ?? att.fileId}`}
                  type="button"
                  className="shared-media-item"
                  onClick={() => setViewer(idx)}
                  title={att.originalName ?? 'Open image'}
                >
                  {urls[att.fileId] ? (
                    <img
                      src={urls[att.fileId]}
                      alt={att.originalName ?? 'Shared image'}
                      className="shared-media-image"
                      loading="lazy"
                    />
                  ) : (
                    <Skeleton width="100%" height="100%" borderRadius="0" />
                  )}
                </button>
              ))}
            </div>
          ) : (
            <EmptyState
              title="No media yet"
              description="Shared images will appear here"
              icon={
                <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                  <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
                  <circle cx="8.5" cy="8.5" r="1.5" />
                  <polyline points="21 15 16 10 5 21" />
                </svg>
              }
            />
          )
        ) : files.length > 0 ? (
          <div className="shared-files-list">
            {files.map(({ att, createdAt, messageId }) => (
              <div key={`${messageId}-${att.id ?? att.fileId}`} className="shared-file-item">
                <div className="shared-file-icon">
                  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                    <polyline points="14 2 14 8 20 8" />
                  </svg>
                </div>
                <div className="shared-file-info">
                  <span className="shared-file-name">{att.originalName ?? 'Unknown file'}</span>
                  <span className="shared-file-size">
                    {[att.sizeBytes ? formatFileSize(att.sizeBytes) : null, formatShortDate(createdAt)]
                      .filter(Boolean)
                      .join(' · ')}
                  </span>
                </div>
                <button
                  className="shared-file-download"
                  onClick={() => void openFile(att.fileId)}
                  aria-label={`Download ${att.originalName ?? 'file'}`}
                >
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                    <polyline points="7 10 12 15 17 10" />
                    <line x1="12" y1="15" x2="12" y2="3" />
                  </svg>
                </button>
              </div>
            ))}
          </div>
        ) : (
          <EmptyState
            title="No files yet"
            description="Shared files will appear here"
            icon={
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                <polyline points="14 2 14 8 20 8" />
              </svg>
            }
          />
        )}
      </div>
      {viewer !== null && (
        <ImageLightbox
          images={images.map(({ att }) => ({ fileId: att.fileId, name: att.originalName }))}
          urls={urls}
          startIndex={viewer}
          onClose={() => setViewer(null)}
        />
      )}
    </div>
    </div>
  );
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function isImage(att: MessageAttachment): boolean {
  return att.attachmentType === 'IMAGE' || att.mimeType?.startsWith('image/') === true;
}

function formatShortDate(iso: string): string {
  return new Date(iso).toLocaleDateString([], { month: 'short', day: 'numeric' });
}
