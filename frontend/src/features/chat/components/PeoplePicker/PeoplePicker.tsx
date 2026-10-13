import React, { useEffect, useMemo, useState } from 'react';
import { Avatar } from '../../../../shared/components/ui';
import type { UserSearchResult } from '../../../../shared/types';
import { userApi } from '../../api/userApi';
import './PeoplePicker.css';

interface PeoplePickerProps {
  selected: number[];
  onChange: (ids: number[]) => void;
  /** People who must not be offered (already in the group or call). */
  excludeIds?: number[];
  /** Most people that can be selected. */
  max?: number;
}

/** Searchable multi-select list of people, shared by "new group" and "add to call". */
export function PeoplePicker({ selected, onChange, excludeIds = [], max }: PeoplePickerProps) {
  const [users, setUsers] = useState<UserSearchResult[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState('');

  useEffect(() => {
    let cancelled = false;
    userApi
      .list()
      .then((list) => {
        if (!cancelled) setUsers(list);
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Failed to load people');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const excluded = useMemo(() => new Set(excludeIds), [excludeIds]);
  const available = useMemo(() => users.filter((u) => !excluded.has(u.id)), [users, excluded]);
  const q = query.trim().toLowerCase();
  const visible = q
    ? available.filter((u) => u.displayName.toLowerCase().includes(q) || u.username.toLowerCase().includes(q))
    : available;
  const byId = useMemo(() => new Map(users.map((u) => [u.id, u])), [users]);
  const atLimit = max !== undefined && selected.length >= max;

  const toggle = (id: number) => {
    if (selected.includes(id)) onChange(selected.filter((x) => x !== id));
    else if (!atLimit) onChange([...selected, id]);
  };

  return (
    <div className="people-picker">
      {selected.length > 0 && (
        <div className="people-picker-chips" aria-label="Selected people">
          {selected.map((id) => (
            <button key={id} type="button" className="people-chip" onClick={() => toggle(id)} aria-label={`Remove ${byId.get(id)?.displayName ?? 'person'}`}>
              {byId.get(id)?.displayName ?? `User ${id}`}
              <span aria-hidden="true">×</span>
            </button>
          ))}
        </div>
      )}

      <input
        className="people-picker-search"
        type="search"
        placeholder="Search people..."
        aria-label="Search people"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
      />

      <div className="people-picker-list" role="listbox" aria-multiselectable="true">
        {loading && <p className="people-picker-note">Loading...</p>}
        {error && <p className="people-picker-note people-picker-note--error">{error}</p>}
        {!loading && !error && visible.length === 0 && (
          <p className="people-picker-note">{q ? 'No one matches your search' : 'No one to add'}</p>
        )}
        {visible.map((u) => {
          const checked = selected.includes(u.id);
          const disabled = !checked && atLimit;
          return (
            <button
              key={u.id}
              type="button"
              role="option"
              aria-selected={checked}
              disabled={disabled}
              className={`people-row${checked ? ' people-row--on' : ''}`}
              onClick={() => toggle(u.id)}
            >
              <Avatar name={u.displayName} src={u.avatarUrl} size="sm" online={u.online} />
              <span className="people-row-text">
                <span className="people-row-name">{u.displayName}</span>
                <span className="people-row-sub">@{u.username}</span>
              </span>
              <span className={`people-check${checked ? ' people-check--on' : ''}`} aria-hidden="true">
                {checked && (
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3.5" strokeLinecap="round" strokeLinejoin="round">
                    <polyline points="20 6 9 17 4 12" />
                  </svg>
                )}
              </span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
