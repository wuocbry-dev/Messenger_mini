/**
 * useWebSocket — manages a STOMP-over-WebSocket connection.
 * Automatically reconnects on disconnect and routes messages to the
 * appropriate handlers. Also dispatches `chat:stomp-*` and
 * `messenger:call-event` CustomEvents on the document so feature
 * components (ChatPanel, CallProvider) can subscribe without prop
 * drilling.
 */
import { useEffect, useRef, useCallback, useState } from 'react';
import { Stomp, type Client } from 'stompjs/lib/stomp.js';
import { ensureFreshToken } from '../auth/refresh';
import { tokenStorage } from '../auth/tokenStorage';

// Default: same origin (ws/wss follows the page protocol) via the Vite dev proxy.
const WS_URL =
  import.meta.env.VITE_WS_URL ??
  `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}/ws`;

export interface WsHandlers {
  onMessage?: (payload: unknown) => void;
  onTyping?: (payload: unknown) => void;
  onReceipt?: (payload: unknown) => void;
  onPresence?: (payload: unknown) => void;
  onCall?: (payload: unknown) => void;
}

export type WsStatus = 'disconnected' | 'connecting' | 'connected' | 'error';

const CHAT_MESSAGE_EVENT = 'chat:stomp-message';
const CHAT_TYPING_EVENT = 'chat:stomp-typing';
const CHAT_RECEIPT_EVENT = 'chat:stomp-receipt';
const CALL_EVENT_NAME = 'messenger:call-event';
const PRESENCE_EVENT = 'chat:stomp-presence';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function dispatch(name: string, detail: any): void {
  if (typeof document === 'undefined') return;
  document.dispatchEvent(new CustomEvent(name, { detail }));
}

export function useWebSocket(handlers: WsHandlers) {
  const [status, setStatus] = useState<WsStatus>('disconnected');
  const clientRef = useRef<Client | null>(null);
  const reconnectTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  // Bumped by every connect and by unmount. connect() awaits a token refresh,
  // so an older call that resumes late must not open a second connection
  // (every event would then arrive twice).
  const attemptRef = useRef(0);
  const handlersRef = useRef(handlers);
  handlersRef.current = handlers;

  const connect = useCallback(async () => {
    const attempt = ++attemptRef.current;
    if (reconnectTimer.current) {
      clearTimeout(reconnectTimer.current);
      reconnectTimer.current = null;
    }
    const previous = clientRef.current;
    clientRef.current = null;
    try {
      previous?.disconnect();
    } catch {
      /* ignore */
    }
    // Renew the access token first if it is about to expire, so reconnects after
    // sleep or a long offline period still authenticate.
    await ensureFreshToken();
    if (attempt !== attemptRef.current) return; // superseded or unmounted meanwhile
    const token = tokenStorage.getAccess();
    if (!token) {
      setStatus('disconnected');
      return;
    }

    setStatus('connecting');

    try {
      // The backend exposes a native WebSocket STOMP endpoint (no SockJS).
      const client = Stomp.client(WS_URL);
      client.debug = null;
      // Match the server heartbeat so a dead connection is noticed (and
      // presence updated) within about 20 seconds.
      client.heartbeat.outgoing = 10_000;
      client.heartbeat.incoming = 10_000;
      clientRef.current = client;

      const safeParse = (frame: { body: string }): unknown => {
        try {
          return JSON.parse(frame.body);
        } catch (err) {
          console.error('Failed to parse STOMP frame', err);
          return null;
        }
      };

      client.connect(
        { Authorization: `Bearer ${token}` },
        () => {
          setStatus('connected');
          // Spring resolves `/user/queue/...` to the authenticated session's
          // private queue; the server sends with convertAndSendToUser.
          client.subscribe('/user/queue/messages', (frame) => {
            const data = safeParse(frame);
            if (data != null) {
              handlersRef.current.onMessage?.(data);
              dispatch(CHAT_MESSAGE_EVENT, data);
            }
          });
          client.subscribe('/user/queue/typing', (frame) => {
            const data = safeParse(frame);
            if (data != null) {
              handlersRef.current.onTyping?.(data);
              dispatch(CHAT_TYPING_EVENT, data);
            }
          });
          client.subscribe('/user/queue/receipts', (frame) => {
            const data = safeParse(frame);
            if (data != null) {
              handlersRef.current.onReceipt?.(data);
              dispatch(CHAT_RECEIPT_EVENT, data);
            }
          });
          client.subscribe('/user/queue/presence', (frame) => {
            const data = safeParse(frame);
            if (data != null) {
              handlersRef.current.onPresence?.(data);
              dispatch(PRESENCE_EVENT, data);
            }
          });
          client.subscribe('/user/queue/calls', (frame) => {
            const data = safeParse(frame);
            if (data != null) {
              handlersRef.current.onCall?.(data);
              dispatch(CALL_EVENT_NAME, data);
            }
          });
        },
        // stompjs 2.x calls this both for ERROR frames and when the socket
        // closes, so it is also the reconnect hook.
        (err) => {
          if (clientRef.current !== client) return; // superseded or unmounted
          console.error('STOMP error', err);
          setStatus('error');
          reconnectTimer.current = setTimeout(() => {
            void connect();
          }, 1500);
        }
      );
    } catch (err) {
      console.error('WS connection failed', err);
      setStatus('error');
      reconnectTimer.current = setTimeout(() => {
        void connect();
      }, 5000);
    }
  }, []);

  // Reconnect right away when the network returns or the tab becomes visible
  // again, instead of waiting for the retry timer.
  useEffect(() => {
    const revive = () => {
      if (document.visibilityState === 'hidden') return;
      if (!clientRef.current?.connected) void connect();
    };
    window.addEventListener('online', revive);
    document.addEventListener('visibilitychange', revive);
    return () => {
      window.removeEventListener('online', revive);
      document.removeEventListener('visibilitychange', revive);
    };
  }, [connect]);

  useEffect(() => {
    void connect();
    return () => {
      attemptRef.current++;
      if (reconnectTimer.current) {
        clearTimeout(reconnectTimer.current);
        reconnectTimer.current = null;
      }
      try {
        clientRef.current?.disconnect();
      } catch {
        /* ignore */
      }
      clientRef.current = null;
    };
  }, [connect]);

  const send = useCallback((destination: string, body?: unknown) => {
    const client = clientRef.current;
    if (!client?.connected) return;
    const payload = body != null ? JSON.stringify(body) : undefined;
    client.send(destination, {}, payload);
  }, []);

  return { status, send, reconnect: connect };
}
