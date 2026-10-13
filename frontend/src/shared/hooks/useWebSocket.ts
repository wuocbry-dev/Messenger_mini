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

// WebSocket URL: dùng env hoặc tự detect từ trình duyệt
const getWsUrl = () => {
  if (import.meta.env.VITE_WS_URL) return import.meta.env.VITE_WS_URL;
  // Proxy qua Vite dev server (ws://localhost:5173/ws → ws://localhost:8080/ws)
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocol}//${window.location.host}/ws`;
};
const WS_URL = getWsUrl();

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

/**
 * Close a STOMP client in any state. stompjs' `disconnect()` throws/does
 * nothing while the socket is still connecting, which left a second live
 * session behind (React StrictMode mounts effects twice) and made every
 * event fire twice.
 */
function closeClient(client: Client | null): void {
  if (!client) return;
  try {
    if (client.connected) {
      client.disconnect();
    }
  } catch {
    /* ignore */
  }
  try {
    client.ws?.close();
  } catch {
    /* ignore */
  }
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function dispatch(name: string, detail: any): void {
  if (typeof document === 'undefined') return;
  document.dispatchEvent(new CustomEvent(name, { detail }));
}

export function useWebSocket(handlers: WsHandlers) {
  const [status, setStatus] = useState<WsStatus>('disconnected');
  const clientRef = useRef<Client | null>(null);
  const reconnectTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const handlersRef = useRef(handlers);
  handlersRef.current = handlers;

  const connect = useCallback(async () => {
    if (reconnectTimer.current) {
      clearTimeout(reconnectTimer.current);
      reconnectTimer.current = null;
    }
    const previous = clientRef.current;
    clientRef.current = null;
    closeClient(previous);
    const token = sessionStorage.getItem('accessToken');
    if (!token) {
      setStatus('disconnected');
      return;
    }

    setStatus('connecting');

    try {
      // The backend exposes a native WebSocket STOMP endpoint (no SockJS).
      const client = Stomp.client(WS_URL);
      client.debug = null;
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
          if (clientRef.current !== client) {
            // Superseded (reconnect or unmount) while the handshake was in flight.
            closeClient(client);
            return;
          }
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
          }, 3000);
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

  useEffect(() => {
    void connect();
    return () => {
      if (reconnectTimer.current) {
        clearTimeout(reconnectTimer.current);
        reconnectTimer.current = null;
      }
      const client = clientRef.current;
      clientRef.current = null;
      closeClient(client);
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
