import React, { useState, useRef, useCallback, useEffect } from 'react';
import './MessageInput.css';

interface MessageInputProps {
  conversationId: number;
  onSend: (content: string) => void;
  onTyping: (typing: boolean) => void;
  /** Resolve `false` to signal failure so the staged files are kept for a retry. */
  onAttach?: (files: File[]) => Promise<boolean | void> | boolean | void;
  disabled?: boolean;
}

interface StagedFile {
  id: string;
  file: File;
  /** Object URL for image previews. */
  preview?: string;
}

const ACCEPT = 'image/jpeg,image/png,image/gif,image/webp,application/pdf,text/plain,application/zip';
const MAX_BYTES = 50 * 1024 * 1024;
const MAX_STAGED = 20;

let stagedSeq = 0;

function makeStaged(file: File): StagedFile {
  return {
    id: `${Date.now()}-${stagedSeq++}`,
    file,
    preview: file.type.startsWith('image/') ? URL.createObjectURL(file) : undefined,
  };
}

function shortSize(bytes: number): string {
  if (bytes > 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  return `${Math.max(1, Math.round(bytes / 1024))} KB`;
}

export function MessageInput({ conversationId, onSend, onTyping, onAttach, disabled }: MessageInputProps) {
  const [value, setValue] = useState('');
  const [sending, setSending] = useState(false);
  const [staged, setStaged] = useState<StagedFile[]>([]);
  const [notice, setNotice] = useState<string | null>(null);
  const typingTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const stagedRef = useRef<StagedFile[]>([]);
  stagedRef.current = staged;

  const stopTyping = useCallback(() => {
    if (typingTimer.current) {
      clearTimeout(typingTimer.current);
      typingTimer.current = null;
      onTyping(false);
    }
  }, [onTyping]);

  const revoke = (items: StagedFile[]) =>
    items.forEach((s) => s.preview && URL.revokeObjectURL(s.preview));

  // A different conversation starts with an empty tray.
  useEffect(() => {
    revoke(stagedRef.current);
    setStaged([]);
    setNotice(null);
  }, [conversationId]);

  useEffect(() => () => revoke(stagedRef.current), []);

  const addFiles = useCallback((files: File[]) => {
    if (files.length === 0) return;
    const problems: string[] = [];
    const accepted: StagedFile[] = [];
    for (const file of files) {
      if (file.size > MAX_BYTES) {
        problems.push(`${file.name} is larger than 50 MB`);
        continue;
      }
      accepted.push(makeStaged(file));
    }
    setStaged((prev) => {
      const room = Math.max(0, MAX_STAGED - prev.length);
      if (accepted.length > room) problems.push(`You can attach up to ${MAX_STAGED} files at once`);
      revoke(accepted.slice(room));
      return [...prev, ...accepted.slice(0, room)];
    });
    setNotice(problems.length ? problems[0] : null);
  }, []);

  const removeStaged = (id: string) => {
    setStaged((prev) => {
      const target = prev.find((s) => s.id === id);
      if (target) revoke([target]);
      return prev.filter((s) => s.id !== id);
    });
    setNotice(null);
  };

  const handleChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setValue(e.target.value);
    // Auto-resize
    const el = inputRef.current;
    if (el) {
      el.style.height = 'auto';
      el.style.height = `${Math.min(el.scrollHeight, 120)}px`;
    }
    // Typing indicator
    onTyping(true);
    if (typingTimer.current) clearTimeout(typingTimer.current);
    typingTimer.current = setTimeout(stopTyping, 3000);
  };

  const canSend = (value.trim().length > 0 || staged.length > 0) && !sending && !disabled;

  const handleSubmit = useCallback(async (e?: React.FormEvent) => {
    e?.preventDefault();
    const trimmed = value.trim();
    if ((!trimmed && staged.length === 0) || sending) return;
    stopTyping();
    setSending(true);
    setNotice(null);
    try {
      // Attachments go first, then the text, so the order matches the composer.
      if (staged.length > 0 && onAttach) {
        const ok = await onAttach(staged.map((s) => s.file));
        if (ok === false) {
          setNotice('Could not send the files. Please try again.');
          return; // keep the tray and the text for a retry
        }
        revoke(staged);
        setStaged([]);
      }
      if (trimmed) {
        onSend(trimmed);
        setValue('');
        if (inputRef.current) inputRef.current.style.height = 'auto';
      }
    } finally {
      setSending(false);
    }
  }, [value, staged, sending, stopTyping, onSend, onAttach]);

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      void handleSubmit();
    }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files ?? []);
    e.target.value = '';
    addFiles(files);
  };

  const handlePaste = (e: React.ClipboardEvent<HTMLTextAreaElement>) => {
    const files = Array.from(e.clipboardData.files ?? []);
    if (files.length > 0) {
      e.preventDefault();
      addFiles(files);
    }
  };

  useEffect(() => {
    return () => stopTyping();
  }, [stopTyping]);

  return (
    <form className="message-input-form" onSubmit={handleSubmit}>
      {staged.length > 0 && (
        <div className="message-tray" aria-label="Attachments to send">
          <button
            type="button"
            className="message-tray-add"
            onClick={() => fileInputRef.current?.click()}
            disabled={disabled || sending || staged.length >= MAX_STAGED}
            aria-label="Add more files"
            title="Add more files"
          >
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <rect x="8" y="8" width="13" height="13" rx="2" />
              <path d="M16 8V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h3" />
              <path d="M14.5 11.5v6M11.5 14.5h6" />
            </svg>
          </button>
          <div className="message-tray-items">
            {staged.map((item) => (
              <div key={item.id} className={`message-tray-item${item.preview ? '' : ' message-tray-item--file'}`}>
                {item.preview ? (
                  <img src={item.preview} alt={item.file.name} />
                ) : (
                  <>
                    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                      <polyline points="14 2 14 8 20 8" />
                    </svg>
                    <span className="message-tray-name">{item.file.name}</span>
                    <span className="message-tray-size">{shortSize(item.file.size)}</span>
                  </>
                )}
                <button
                  type="button"
                  className="message-tray-remove"
                  onClick={() => removeStaged(item.id)}
                  disabled={sending}
                  aria-label={`Remove ${item.file.name}`}
                >
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round">
                    <line x1="18" y1="6" x2="6" y2="18" />
                    <line x1="6" y1="6" x2="18" y2="18" />
                  </svg>
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      {notice && <p className="message-input-notice" role="alert">{notice}</p>}

      <div className="message-input-wrap">
        <input
          ref={fileInputRef}
          className="message-file-input"
          type="file"
          accept={ACCEPT}
          multiple
          onChange={handleFileChange}
          disabled={disabled || sending}
        />
        <button
          type="button"
          className="message-attach-btn"
          disabled={disabled || sending || !onAttach}
          onClick={() => fileInputRef.current?.click()}
          aria-label="Attach file"
          title="Attach file"
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M21.44 11.05 12.25 20.24a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48" />
          </svg>
        </button>
        <textarea
          ref={inputRef}
          className="message-textarea"
          placeholder={staged.length > 0 ? 'Press send to share these files' : 'Write a message...'}
          value={value}
          onChange={handleChange}
          onKeyDown={handleKeyDown}
          onPaste={handlePaste}
          rows={1}
          disabled={disabled || sending}
          aria-label="Message input"
        />
        <button
          type="submit"
          className="message-send-btn"
          disabled={!canSend}
          aria-label="Send message"
        >
          {sending ? (
            <span className="message-upload-spinner message-upload-spinner--on-send" aria-hidden="true" />
          ) : (
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="22" y1="2" x2="11" y2="13" />
              <polygon points="22 2 15 22 11 13 2 9 22 2" />
            </svg>
          )}
        </button>
      </div>
    </form>
  );
}
