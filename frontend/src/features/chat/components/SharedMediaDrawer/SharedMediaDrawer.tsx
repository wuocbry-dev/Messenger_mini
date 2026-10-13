import React, { useState, useEffect, useCallback } from 'react';
import { EmptyState } from '../../../../shared/components/ui/EmptyState';
import { Skeleton } from '../../../../shared/components/ui/Skeleton';
import type { Conversation, Message } from '../../../../shared/types';
import { messageApi } from '../../api/messageApi';
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
        setMessages(msgs.reverse());
      })
      .catch(console.error)
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [open, conversation?.id]);

  const handleDownload = useCallback(async (fileId: number, filename: string) => {
    try {
      const response = await messageApi.getDownloadUrl(fileId);
      if (response.downloadUrl) {
        const link = document.createElement('a');
        link.href = response.downloadUrl;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
      }
    } catch (err) {
      console.error('Download failed:', err);
    }
  }, []);

  const imageMessages = messages.filter((m) => m.type === 'IMAGE');
  const fileMessages = messages.filter((m) => m.type === 'FILE');

  if (!open) return null;

  return (
    <div className="shared-media-drawer">
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
          Media
        </button>
        <button
          className={`shared-media-tab${activeTab === 'files' ? ' shared-media-tab--active' : ''}`}
          onClick={() => setActiveTab('files')}
        >
          Files
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
          imageMessages.length > 0 ? (
            <div className="shared-media-grid">
              {imageMessages.map((msg) => (
                <div key={msg.id} className="shared-media-item">
                  {msg.downloadUrl ? (
                    <img
                      src={msg.downloadUrl}
                      alt={msg.fileName ?? 'Shared image'}
                      className="shared-media-image"
                      loading="lazy"
                    />
                  ) : (
                    <div className="shared-media-placeholder">Image</div>
                  )}
                </div>
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
        ) : fileMessages.length > 0 ? (
          <div className="shared-files-list">
            {fileMessages.map((msg) => (
              <div key={msg.id} className="shared-file-item">
                <div className="shared-file-icon">
                  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                    <polyline points="14 2 14 8 20 8" />
                  </svg>
                </div>
                <div className="shared-file-info">
                  <span className="shared-file-name">{msg.fileName ?? 'Unknown file'}</span>
                  {msg.fileSize && (
                    <span className="shared-file-size">
                      {formatFileSize(msg.fileSize)}
                    </span>
                  )}
                </div>
                <button
                  className="shared-file-download"
                  onClick={() => {
                    const fileId = msg.attachments?.[0]?.fileId;
                    if (fileId) handleDownload(fileId, msg.fileName ?? 'download');
                  }}
                  aria-label="Download"
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
    </div>
  );
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
