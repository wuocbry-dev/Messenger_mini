import React, { useState, useCallback, useRef, useEffect } from 'react';
import { Modal, Button, Input, Avatar, Alert } from '../../../../shared/components/ui';
import { userApi } from '../../api/userApi';
import type { Conversation, UserSearchResult } from '../../../../shared/types';
import './NewConversationModal.css';

interface NewConversationModalProps {
  open: boolean;
  onClose: () => void;
  onCreated: (conv: Conversation) => void;
}

export function NewConversationModal({ open, onClose, onCreated }: NewConversationModalProps) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<UserSearchResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const searchTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (!open) {
      setQuery('');
      setResults([]);
      setSelectedId(null);
      setError(null);
    }
  }, [open]);

  const handleQueryChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value;
    setQuery(value);
    setSelectedId(null);
    if (searchTimer.current) clearTimeout(searchTimer.current);
    if (value.trim().length < 2) {
      setResults([]);
      return;
    }
    searchTimer.current = setTimeout(async () => {
      setLoading(true);
      try {
        const res = await userApi.search(value.trim());
        setResults(res);
      } catch {
        setResults([]);
      } finally {
        setLoading(false);
      }
    }, 300);
  }, []);

  const handleCreate = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      if (selectedId == null) return;
      setError(null);
      setCreating(true);
      try {
        const conv = await userApi.createConversation(selectedId);
        onCreated(conv);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to start conversation');
      } finally {
        setCreating(false);
      }
    },
    [selectedId, onCreated]
  );

  return (
    <Modal open={open} onClose={onClose} title="New Conversation" size="sm">
      <form className="new-conv-form" onSubmit={handleCreate}>
        {error && (
          <Alert type="error" message={error} onClose={() => setError(null)} />
        )}

        <div className="new-conv-search">
          <Input
            label="Find someone"
            type="search"
            placeholder="Search by username or name..."
            value={query}
            onChange={handleQueryChange}
            autoFocus
          />
          {loading && (
            <span className="new-conv-loading" aria-live="polite">
              Searching...
            </span>
          )}
        </div>

        {results.length > 0 && (
          <ul className="new-conv-results" role="listbox" aria-label="Search results">
            {results.map((user) => (
              <li
                key={user.id}
                role="option"
                aria-selected={selectedId === user.id}
                className={`new-conv-result${selectedId === user.id ? ' new-conv-result--selected' : ''}`}
                onClick={() => setSelectedId(user.id)}
                onKeyDown={(e) => e.key === 'Enter' && setSelectedId(user.id)}
                tabIndex={0}
              >
                <Avatar name={user.displayName} src={user.avatarObjectKey} size="sm" />
                <div className="new-conv-result-body">
                  <span className="new-conv-result-name">{user.displayName}</span>
                  <span className="new-conv-result-username">@{user.username}</span>
                </div>
                {selectedId === user.id && (
                  <span className="new-conv-result-check" aria-hidden="true">
                    <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                      <circle cx="8" cy="8" r="7" fill="var(--color-primary)" />
                      <path d="M5 8l2 2 4-4" stroke="white" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
                    </svg>
                  </span>
                )}
              </li>
            ))}
          </ul>
        )}

        {query.length >= 2 && !loading && results.length === 0 && (
          <p className="new-conv-empty">No users found matching &ldquo;{query}&rdquo;</p>
        )}

        <div className="new-conv-actions">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button
            type="submit"
            variant="primary"
            loading={creating}
            disabled={selectedId == null}
          >
            Start Chat
          </Button>
        </div>
      </form>
    </Modal>
  );
}
