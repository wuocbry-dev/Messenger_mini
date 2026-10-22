import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { Avatar } from '../../../shared/components/ui/Avatar';
import { EmptyState } from '../../../shared/components/ui/EmptyState';
import { Skeleton } from '../../../shared/components/ui/Skeleton';
import { Alert } from '../../../shared/components/ui/Alert';
import { callApi } from '../api/callApi';
import { userApi } from '../../chat/api/userApi';
import { loadAuthFromStorage } from '../../auth/store/authStore';
import { parseUtc } from '../../../shared/utils/datetime';
import { MOBILE_QUERY } from '../../../shared/hooks/useIsMobile';
import type { CallResponse, UserSearchResult } from '../../../shared/types';
import './CallsPage.css';

type Filter = 'all' | 'missed' | 'voice' | 'video';

const PAGE_SIZE = 30;
const USERS_REFRESH_MS = 30_000;

interface Peer {
  id: number;
  name: string;
  username: string;
  avatarUrl?: string;
  online: boolean;
  /** A call with several people; `id` is then not a user id. */
  group?: boolean;
}

// ─── formatting helpers ───────────────────────────────────────────

function startOfDay(t: number): number {
  const d = new Date(t);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

function formatDay(iso: string): string {
  const t = parseUtc(iso);
  if (t === null) return '';
  const days = Math.round((startOfDay(Date.now()) - startOfDay(t)) / 86_400_000);
  if (days === 0) return 'Today';
  if (days === 1) return 'Yesterday';
  return new Date(t).toLocaleDateString([], { month: 'short', day: 'numeric', year: 'numeric' });
}

function formatClock(iso?: string | null): string {
  const t = parseUtc(iso);
  return t === null ? '' : new Date(t).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
}

function durationSeconds(call: CallResponse): number | null {
  const a = parseUtc(call.answeredAt);
  const e = parseUtc(call.endedAt);
  if (a === null || e === null) return null;
  return Math.max(0, Math.round((e - a) / 1000));
}

function formatDuration(call: CallResponse): string {
  const total = durationSeconds(call);
  if (total === null) return '—';
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  if (h > 0) return `${h} hr ${m} min`;
  if (m > 0) return `${m} min ${s.toString().padStart(2, '0')} sec`;
  return `${s} sec`;
}

const STATUS_LABEL: Record<string, string> = {
  ENDED: 'Ended',
  MISSED: 'Missed',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',
  FAILED: 'Failed',
  RINGING: 'Ringing',
  ACCEPTED: 'In progress',
};

/** I was not the caller, so the call was incoming for me. */
function isIncoming(call: CallResponse, me?: number): boolean {
  return call.callerId !== me;
}

function isMissedFor(call: CallResponse, me?: number): boolean {
  const mine = call.participants.find((p) => p.userId === me);
  return (
    call.status === 'MISSED' ||
    mine?.state === 'MISSED' ||
    (call.status === 'CANCELLED' && isIncoming(call, me))
  );
}

function statusTone(call: CallResponse, me?: number): 'bad' | 'ok' | 'neutral' {
  if (isMissedFor(call, me) || call.status === 'REJECTED' || call.status === 'FAILED') return 'bad';
  if (call.status === 'ENDED') return 'neutral';
  return 'ok';
}

function describe(call: CallResponse, me?: number): string {
  const kind = call.callType === 'VIDEO' ? 'video' : 'voice';
  if (isMissedFor(call, me)) return `Missed ${kind} call`;
  if (call.status === 'REJECTED') return `Rejected ${kind} call`;
  if (call.status === 'CANCELLED') return `Cancelled ${kind} call`;
  return `${call.groupCall ? 'Group ' : ''}${isIncoming(call, me) ? 'Incoming' : 'Outgoing'} ${kind} call`;
}

// ─── icons ────────────────────────────────────────────────────────

const svgProps = {
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 2,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
};

const PhoneIcon = ({ size = 18 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" {...svgProps} aria-hidden="true">
    <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07A19.5 19.5 0 0 1 4.69 12 19.79 19.79 0 0 1 1.61 3.38 2 2 0 0 1 3.6 1.22h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z" />
  </svg>
);

const VideoIcon = ({ size = 18 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" {...svgProps} aria-hidden="true">
    <polygon points="23 7 16 12 23 17 23 7" />
    <rect x="1" y="5" width="15" height="14" rx="2" ry="2" />
  </svg>
);

const ChatIcon = ({ size = 18 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" {...svgProps} aria-hidden="true">
    <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
  </svg>
);

const MoreIcon = ({ size = 18 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
    <circle cx="5" cy="12" r="1.8" />
    <circle cx="12" cy="12" r="1.8" />
    <circle cx="19" cy="12" r="1.8" />
  </svg>
);

function DirectionIcon({ call, me }: { call: CallResponse; me?: number }) {
  const missed = isMissedFor(call, me) || call.status === 'REJECTED';
  const incoming = isIncoming(call, me);
  const tone = missed ? 'call-dir--bad' : incoming ? 'call-dir--in' : 'call-dir--out';
  return (
    <span className={`call-dir ${tone}`} aria-hidden="true">
      {call.callType === 'VIDEO' ? <VideoIcon size={14} /> : <PhoneIcon size={14} />}
    </span>
  );
}

// ─── page ─────────────────────────────────────────────────────────

export function CallsPage() {
  const navigate = useNavigate();
  const me = loadAuthFromStorage().user?.id;

  const [calls, setCalls] = useState<CallResponse[]>([]);
  const [users, setUsers] = useState<Map<number, UserSearchResult>>(new Map());
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [hasNext, setHasNext] = useState(false);
  const [page, setPage] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState<Filter>('all');
  const [selectedId, setSelectedId] = useState<number | null>(null);
  // Desktop shows the newest call's details beside the list; on phones the
  // details are a full-screen page, so the history list comes first.
  const [panelOpen, setPanelOpen] = useState(() => !window.matchMedia(MOBILE_QUERY).matches);
  const [menuId, setMenuId] = useState<number | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    let cancelled = false;
    callApi
      .getHistory({ page: 0, size: PAGE_SIZE })
      .then((res) => {
        if (cancelled) return;
        setCalls(res.calls);
        setHasNext(res.hasNext);
        setPage(0);
        setSelectedId((id) => id ?? res.calls[0]?.id ?? null);
      })
      .catch((err) => !cancelled && setError(err instanceof Error ? err.message : 'Failed to load call history'))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, []);

  // People directory gives display names, avatars and online status.
  useEffect(() => {
    let cancelled = false;
    const load = () =>
      userApi
        .list()
        .then((list) => !cancelled && setUsers(new Map(list.map((u) => [u.id, u]))))
        .catch(() => {});
    void load();
    const timer = setInterval(() => void load(), USERS_REFRESH_MS);
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, []);

  useEffect(() => {
    if (menuId === null) return;
    const close = () => setMenuId(null);
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && close();
    document.addEventListener('click', close);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('click', close);
      document.removeEventListener('keydown', onKey);
    };
  }, [menuId]);

  const peerOf = useCallback(
    (call: CallResponse): Peer => {
      if (call.groupCall) {
        const others = call.participants.filter((p) => p.userId !== me);
        const names = others.slice(0, 3).map((p) => p.displayName);
        const extra = others.length - names.length;
        return {
          id: -call.id,
          name: names.join(', ') + (extra > 0 ? ` +${extra}` : ''),
          username: others.map((p) => p.username).join(', '),
          avatarUrl: others[0]?.avatarUrl ?? undefined,
          online: false,
          group: true,
        };
      }
      const outgoing = call.callerId === me;
      const other = call.participants.find((p) => p.userId !== me);
      const id = (outgoing ? call.calleeId ?? other?.userId : call.callerId) ?? 0;
      const username = (outgoing ? call.calleeUsername ?? other?.username : call.callerUsername) ?? '?';
      const user = users.get(id);
      return {
        id,
        name: user?.displayName ?? username,
        username: user?.username ?? username,
        avatarUrl: user?.avatarUrl,
        online: user?.online ?? false,
      };
    },
    [me, users]
  );

  const counts = useMemo(
    () => ({
      all: calls.length,
      missed: calls.filter((c) => isMissedFor(c, me)).length,
      voice: calls.filter((c) => c.callType === 'VOICE').length,
      video: calls.filter((c) => c.callType === 'VIDEO').length,
    }),
    [calls, me]
  );

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    return calls.filter((c) => {
      if (filter === 'missed' && !isMissedFor(c, me)) return false;
      if (filter === 'voice' && c.callType !== 'VOICE') return false;
      if (filter === 'video' && c.callType !== 'VIDEO') return false;
      if (!q) return true;
      const p = peerOf(c);
      return p.name.toLowerCase().includes(q) || p.username.toLowerCase().includes(q);
    });
  }, [calls, filter, query, me, peerOf]);

  const selected = calls.find((c) => c.id === selectedId) ?? null;
  const selectedPeer = selected ? peerOf(selected) : null;
  const latestWithPeer = selectedPeer ? calls.find((c) => peerOf(c).id === selectedPeer.id) ?? null : null;

  const loadMore = useCallback(async () => {
    setLoadingMore(true);
    try {
      const res = await callApi.getHistory({ page: page + 1, size: PAGE_SIZE });
      setCalls((prev) => [...prev, ...res.calls.filter((c) => !prev.some((p) => p.id === c.id))]);
      setHasNext(res.hasNext);
      setPage(page + 1);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load more calls');
    } finally {
      setLoadingMore(false);
    }
  }, [page]);

  const startCall = useCallback(
    (call: CallResponse, callType: 'VOICE' | 'VIDEO') => {
      const peer = peerOf(call);
      navigate('/', {
        state: {
          startCall: {
            conversationId: call.conversationId,
            calleeId: peer.group ? null : peer.id,
            group: call.groupCall,
            callType,
            peerName: peer.name,
            peerAvatar: peer.avatarUrl,
            nonce: Date.now(),
          },
        },
      });
    },
    [navigate, peerOf]
  );

  const openChat = useCallback(
    (call: CallResponse) => navigate('/', { state: { openConversationId: call.conversationId } }),
    [navigate]
  );

  const copyUsername = useCallback(async (peer: Peer) => {
    try {
      const text = peer.group
        ? peer.username.split(', ').map((u) => `@${u}`).join(' ')
        : `@${peer.username}`;
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      setError('Could not copy to clipboard');
    }
  }, []);

  const select = (call: CallResponse) => {
    setSelectedId(call.id);
    setPanelOpen(true);
  };

  const filters: { key: Filter; label: string; count: number }[] = [
    { key: 'all', label: 'All', count: counts.all },
    { key: 'missed', label: 'Missed', count: counts.missed },
    { key: 'voice', label: 'Voice', count: counts.voice },
    { key: 'video', label: 'Video', count: counts.video },
  ];

  return (
    <div className="calls-page">
      <div className="calls-list-pane">
        <header className="calls-header">
          <h1 className="calls-page-title">Call History</h1>
          <div className="calls-search-wrap">
            <svg className="calls-search-icon" width="18" height="18" viewBox="0 0 24 24" {...svgProps} aria-hidden="true">
              <circle cx="11" cy="11" r="8" />
              <path d="M21 21l-4.35-4.35" />
            </svg>
            <input
              type="search"
              className="calls-search-input"
              placeholder="Search calls by name or username..."
              aria-label="Search calls"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
            />
          </div>
          <div className="calls-filters" role="tablist" aria-label="Filter calls">
            {filters.map((f) => (
              <button
                key={f.key}
                role="tab"
                aria-selected={filter === f.key}
                className={`calls-chip${filter === f.key ? ' calls-chip--active' : ''}`}
                onClick={() => setFilter(f.key)}
              >
                {f.label}
                <span className="calls-chip-count">{f.count}</span>
              </button>
            ))}
          </div>
        </header>

        <div className="calls-list" role="list">
          {error && <Alert type="error" message={error} onClose={() => setError(null)} />}

          {loading ? (
            Array.from({ length: 8 }).map((_, i) => (
              <div key={i} className="call-row call-row--skeleton">
                <Skeleton width={48} height={48} borderRadius="var(--radius-full)" />
                <div className="call-row-skeleton-text">
                  <Skeleton width="50%" height={14} />
                  <Skeleton width="35%" height={11} />
                </div>
              </div>
            ))
          ) : visible.length === 0 ? (
            <EmptyState
              title={calls.length === 0 ? 'No call history' : 'No calls match'}
              description={
                calls.length === 0
                  ? 'Your voice and video calls will appear here'
                  : 'Try a different search or filter'
              }
              icon={<PhoneIcon size={48} />}
            />
          ) : (
            <>
              {visible.map((call) => {
                const peer = peerOf(call);
                const tone = statusTone(call, me);
                const active = selectedId === call.id;
                return (
                  <div
                    key={call.id}
                    role="listitem"
                    className={`call-row${active ? ' call-row--active' : ''}`}
                  >
                    <button className="call-row-main" onClick={() => select(call)} aria-label={`View call with ${peer.name}`}>
                      <Avatar name={peer.name} src={peer.avatarUrl} size="lg" online={users.has(peer.id) ? peer.online : undefined} />
                      <span className="call-row-who">
                        <span className="call-row-name">{peer.name}</span>
                        <span className={`call-row-kind call-row-kind--${tone}`}>
                          <DirectionIcon call={call} me={me} />
                          {describe(call, me)}
                        </span>
                      </span>
                      <span className={`call-pill call-pill--${tone}`}>{STATUS_LABEL[call.status] ?? call.status}</span>
                      <span className="call-row-when">
                        <span>{formatDay(call.startedAt)}</span>
                        <span className="call-row-clock">{formatClock(call.startedAt)}</span>
                      </span>
                      <span className="call-row-duration">{formatDuration(call)}</span>
                    </button>

                    {/* Phone layout: one-tap call back with the same call type. */}
                    <button
                      className="call-row-callback"
                      onClick={() => startCall(call, call.callType === 'VIDEO' ? 'VIDEO' : 'VOICE')}
                      aria-label={`${call.callType === 'VIDEO' ? 'Video call' : 'Call'} ${peer.name} again`}
                    >
                      {call.callType === 'VIDEO' ? <VideoIcon /> : <PhoneIcon />}
                    </button>

                    <div className="call-row-menu-wrap">
                      <button
                        className="call-row-more"
                        aria-label={`More options for call with ${peer.name}`}
                        aria-haspopup="menu"
                        aria-expanded={menuId === call.id}
                        onClick={(e) => {
                          e.stopPropagation();
                          setMenuId((id) => (id === call.id ? null : call.id));
                        }}
                      >
                        <MoreIcon />
                      </button>
                      {menuId === call.id && (
                        <div className="call-row-menu" role="menu" onClick={(e) => e.stopPropagation()}>
                          <button role="menuitem" onClick={() => { setMenuId(null); startCall(call, 'VOICE'); }}>
                            Call again
                          </button>
                          <button role="menuitem" onClick={() => { setMenuId(null); startCall(call, 'VIDEO'); }}>
                            Video call
                          </button>
                          <button role="menuitem" onClick={() => { setMenuId(null); openChat(call); }}>
                            Message
                          </button>
                          <button role="menuitem" onClick={() => { setMenuId(null); select(call); }}>
                            View details
                          </button>
                        </div>
                      )}
                    </div>
                  </div>
                );
              })}
              {hasNext && (
                <button className="calls-load-more" onClick={() => void loadMore()} disabled={loadingMore}>
                  {loadingMore ? 'Loading...' : 'Load more'}
                </button>
              )}
            </>
          )}
        </div>
      </div>

      {selected && selectedPeer && panelOpen && (
        <aside className="calls-detail" aria-label="Call details">
          <button className="calls-detail-close" onClick={() => setPanelOpen(false)} aria-label="Close details">
            <svg width="18" height="18" viewBox="0 0 24 24" {...svgProps}>
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>

          <div className="calls-detail-top">
            <div className="calls-detail-avatar">
              <Avatar name={selectedPeer.name} src={selectedPeer.avatarUrl} size="xl" online={users.has(selectedPeer.id) ? selectedPeer.online : undefined} />
            </div>
            <h2 className="calls-detail-name">{selectedPeer.name}</h2>
            <span className={`calls-detail-presence${selectedPeer.online ? ' calls-detail-presence--on' : ''}`}>
              <span className="calls-detail-dot" aria-hidden="true" />
              {users.has(selectedPeer.id) ? (selectedPeer.online ? 'Online' : 'Offline') : `@${selectedPeer.username}`}
            </span>
          </div>

          <div className="calls-detail-actions">
            <button className="calls-act" onClick={() => startCall(selected, 'VOICE')}>
              <span className="calls-act-circle calls-act-circle--primary"><PhoneIcon /></span>
              <span className="calls-act-label calls-act-label--primary">Call Again</span>
            </button>
            <button className="calls-act" onClick={() => startCall(selected, 'VIDEO')}>
              <span className="calls-act-circle"><VideoIcon /></span>
              <span className="calls-act-label">Video Call</span>
            </button>
            <button className="calls-act" onClick={() => openChat(selected)}>
              <span className="calls-act-circle"><ChatIcon /></span>
              <span className="calls-act-label">Message</span>
            </button>
            <button className="calls-act" onClick={() => void copyUsername(selectedPeer)}>
              <span className="calls-act-circle"><MoreIcon /></span>
              <span className="calls-act-label">{copied ? 'Copied!' : 'Copy @user'}</span>
            </button>
          </div>

          {latestWithPeer && (
            <section className="calls-section">
              <h3 className="calls-section-title">Latest Call</h3>
              <button className="calls-latest" onClick={() => select(latestWithPeer)}>
                <span className="calls-latest-icon">
                  {latestWithPeer.callType === 'VIDEO' ? <VideoIcon /> : <PhoneIcon />}
                </span>
                <span className="calls-latest-text">
                  <strong>{describe(latestWithPeer, me)}</strong>
                  <span>
                    {formatDay(latestWithPeer.startedAt)}, {formatClock(latestWithPeer.startedAt)}
                  </span>
                  <span>{formatDuration(latestWithPeer)}</span>
                </span>
                <span className={`call-pill call-pill--${statusTone(latestWithPeer, me)}`}>
                  {STATUS_LABEL[latestWithPeer.status] ?? latestWithPeer.status}
                </span>
              </button>
            </section>
          )}

          <section className="calls-section">
            <h3 className="calls-section-title">Call Information</h3>
            <dl className="calls-info">
              <div>
                <dt>Type</dt>
                <dd>{selected.callType === 'VIDEO' ? 'Video Call' : 'Voice Call'}</dd>
              </div>
              <div>
                <dt>Date</dt>
                <dd>
                  {parseUtc(selected.startedAt) !== null
                    ? new Date(parseUtc(selected.startedAt) as number).toLocaleDateString([], { month: 'short', day: 'numeric', year: 'numeric' })
                    : '—'}
                </dd>
              </div>
              <div>
                <dt>Time</dt>
                <dd>
                  {formatClock(selected.startedAt)}
                  {selected.endedAt ? ` – ${formatClock(selected.endedAt)}` : ''}
                </dd>
              </div>
              <div>
                <dt>Duration</dt>
                <dd>{formatDuration(selected)}</dd>
              </div>
              <div>
                <dt>Direction</dt>
                <dd>{isIncoming(selected, me) ? 'Incoming' : 'Outgoing'}</dd>
              </div>
              <div>
                <dt>Status</dt>
                <dd>
                  <span className={`call-pill call-pill--${statusTone(selected, me)}`}>
                    {STATUS_LABEL[selected.status] ?? selected.status}
                  </span>
                </dd>
              </div>
            </dl>
          </section>
        </aside>
      )}
    </div>
  );
}
