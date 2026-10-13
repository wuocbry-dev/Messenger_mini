import React, { createContext, useCallback, useContext, useMemo, useState } from 'react';
import { useWebSocket, type WsStatus } from '../hooks/useWebSocket';
import type { PresenceUpdate } from '../types';

export interface PresenceInfo {
  online: boolean;
  lastSeenAt?: string;
}

interface RealtimeContextValue {
  status: WsStatus;
  send: (destination: string, body?: unknown) => void;
  /** Live presence by user id, fed by the server's presence events. */
  presence: Map<number, PresenceInfo>;
}

const RealtimeContext = createContext<RealtimeContextValue>({
  status: 'disconnected',
  send: () => {},
  presence: new Map(),
});

export function useRealtime(): RealtimeContextValue {
  return useContext(RealtimeContext);
}

/**
 * Owns the single STOMP connection for the signed-in app, so the user stays
 * online (and keeps receiving events) on every page, not just the chat page.
 */
export function RealtimeProvider({ children }: { children: React.ReactNode }) {
  const [presence, setPresence] = useState<Map<number, PresenceInfo>>(new Map());

  const onPresence = useCallback((payload: unknown) => {
    const p = payload as PresenceUpdate;
    if (p?.userId == null) return;
    setPresence((prev) => {
      const old = prev.get(p.userId);
      if (old && old.online === p.online && old.lastSeenAt === p.lastSeenAt) return prev;
      const next = new Map(prev);
      next.set(p.userId, { online: p.online, lastSeenAt: p.lastSeenAt ?? old?.lastSeenAt });
      return next;
    });
  }, []);

  const { send, status } = useWebSocket({ onPresence });

  const value = useMemo(() => ({ status, send, presence }), [status, send, presence]);
  return <RealtimeContext.Provider value={value}>{children}</RealtimeContext.Provider>;
}
