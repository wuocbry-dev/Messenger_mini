import React, { useState, useRef, useCallback, useEffect } from 'react';
import './MessageInput.css';

interface MessageInputProps {
  conversationId: number;
  onSend: (content: string) => void;
  onTyping: (typing: boolean) => void;
  onAttach?: (file: File) => Promise<void> | void;
  disabled?: boolean;
}

export function MessageInput({ conversationId, onSend, onTyping, onAttach, disabled }: MessageInputProps) {
  const [value, setValue] = useState('');
  const [sending, setSending] = useState(false);
  const [uploading, setUploading] = useState(false);
  const typingTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const stopTyping = useCallback(() => {
    if (typingTimer.current) {
      clearTimeout(typingTimer.current);
      typingTimer.current = null;
      onTyping(false);
    }
  }, [onTyping]);

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

  const handleSubmit = useCallback(async (e?: React.FormEvent) => {
    e?.preventDefault();
    const trimmed = value.trim();
    if (!trimmed || sending) return;
    stopTyping();
    setSending(true);
    try {
      onSend(trimmed);
      setValue('');
      if (inputRef.current) {
        inputRef.current.style.height = 'auto';
      }
    } finally {
      setSending(false);
    }
  }, [value, sending, stopTyping, onSend]);

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSubmit();
    }
  };

  const handleFileChange = useCallback(async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file || !onAttach || uploading) return;
    setUploading(true);
    try {
      await onAttach(file);
    } finally {
      setUploading(false);
    }
  }, [onAttach, uploading]);

  useEffect(() => {
    return () => stopTyping();
  }, [stopTyping]);

  return (
    <form className="message-input-form" onSubmit={handleSubmit}>
      <div className="message-input-wrap">
        <input
          ref={fileInputRef}
          className="message-file-input"
          type="file"
          accept="image/jpeg,image/png,image/gif,image/webp,application/pdf,text/plain,application/zip"
          onChange={handleFileChange}
          disabled={disabled || uploading}
        />
        <button
          type="button"
          className="message-attach-btn"
          disabled={disabled || uploading || !onAttach}
          onClick={() => fileInputRef.current?.click()}
          aria-label="Attach file"
          title="Attach file"
        >
          {uploading ? (
            <span className="message-upload-spinner" aria-hidden="true" />
          ) : (
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M21.44 11.05 12.25 20.24a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48" />
            </svg>
          )}
        </button>
        <textarea
          ref={inputRef}
          className="message-textarea"
          placeholder="Write a message..."
          value={value}
          onChange={handleChange}
          onKeyDown={handleKeyDown}
          rows={1}
          disabled={disabled || sending || uploading}
          aria-label="Message input"
        />
        <button
          type="submit"
          className="message-send-btn"
          disabled={!value.trim() || sending}
          aria-label="Send message"
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <line x1="22" y1="2" x2="11" y2="13" />
            <polygon points="22 2 15 22 11 13 2 9 22 2" />
          </svg>
        </button>
      </div>
    </form>
  );
}
