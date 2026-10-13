import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { ChatPanel } from '../features/chat/components/ChatPanel';
import { CallProvider } from '../features/call/components/CallProvider';
import { useWebSocket } from '../shared/hooks/useWebSocket';
import { conversationApi } from '../features/chat/api/conversationApi';
import { authApi } from '../features/auth/api/authApi';
import { clearAuth } from '../features/auth/store/authStore';
import { ApiRequestError } from '../shared/api';
import { ToastContainer, type ToastData } from '../shared/components/ui/Toast';
import type {
  Conversation,
  Message,
  TypingIndicator,
  MessageReceipt,
  PresenceUpdate,
  CallEvent,
} from '../shared/types';

function generateId() {
  return Math.random().toString(36).slice(2);
}

interface UserSummary {
  id: number;
  username: string;
  displayName: string;
  avatarUrl?: string;
}

export function MessengerPage() {
  const navigate = useNavigate();
  const [user, setUser] = useState<UserSummary | null>(null);
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [convLoading, setConvLoading] = useState(true);
  const [activeConv, setActiveConv] = useState<Conversation | null>(null);
  const [onlineUsers, setOnlineUsers] = useState<Set<number>>(new Set());
  const [toasts, setToasts] = useState<ToastData[]>([]);
  const userMapRef = useRef<
    Map<number, { displayName: string; username?: string; avatarUrl?: string }>
  >(new Map());

  const addToast = useCallback((type: ToastData['type'], message: string) => {
    const id = generateId();
    setToasts((prev) => [...prev, { id, type, message }]);
  }, []);

  const removeToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const normalizeConversation = useCallback((conv: Conversation): Conversation => {
    if (conv.directWithUser || conv.type !== 'DIRECT' || !user) return conv;
    const otherMember = conv.members?.find((member) => member.userId !== user.id);
    if (!otherMember) return conv;
    return {
      ...conv,
      directWithUser: {
        id: otherMember.userId,
        username: otherMember.username,
        displayName: otherMember.displayName,
        avatarObjectKey: otherMember.avatarObjectKey,
        avatarUrl: otherMember.avatarUrl,
      },
    };
  }, [user]);

  // Load current user
  useEffect(() => {
    let cancelled = false;
    authApi
      .me()
      .then((u) => {
        if (cancelled) return;
        setUser({
          id: u.id,
          username: u.username,
          displayName: u.displayName,
          avatarUrl: u.avatarUrl,
        });
        userMapRef.current.set(u.id, {
          displayName: u.displayName,
          username: u.username,
        });
      })
      .catch((err) => {
        if (cancelled) return;
        if (err instanceof ApiRequestError && err.status === 401) {
          clearAuth();
          navigate('/auth', { replace: true });
          return;
        }
        addToast('error', err instanceof Error ? err.message : 'Failed to load your profile');
      });
    return () => {
      cancelled = true;
    };
  }, [addToast, navigate]);

  // Load conversations
  const loadConversations = useCallback(() => {
    if (!user) return;
    setConvLoading(true);
    conversationApi
      .list()
      .then(async (items) => {
        const details = await Promise.all(
          items.map((item) => conversationApi.get(item.id).catch(() => item))
        );
        const normalized = details.map(normalizeConversation);
        setConversations(normalized);
        normalized.forEach((c) => {
          if (c.directWithUser) {
            userMapRef.current.set(c.directWithUser.id, {
              displayName: c.directWithUser.displayName,
              username: c.directWithUser.username,
              avatarUrl: c.directWithUser.avatarUrl,
            });
          }
        });
      })
      .catch((err) => addToast('error', err.message ?? 'Failed to load conversations'))
      .finally(() => setConvLoading(false));
  }, [user, addToast, normalizeConversation]);

  useEffect(() => {
    void loadConversations();
  }, [loadConversations]);

  // ─── WebSocket handlers ───────────────────────────────────────────
  const handleMessage = useCallback((payload: unknown) => {
    const msg = payload as Message;
    if (!msg?.conversationId) return;
    setConversations((prev) =>
      prev
        .map((c) =>
          c.id === msg.conversationId
            ? { ...c, lastMessage: msg, lastMessageAt: msg.createdAt }
            : c
        )
        .sort(
          (a, b) =>
            new Date(b.lastMessageAt ?? 0).getTime() -
            new Date(a.lastMessageAt ?? 0).getTime()
        )
    );
  }, []);

  const handleTypingEvent = useCallback((payload: unknown) => {
    const t = payload as TypingIndicator;
    if (t?.senderId) {
      userMapRef.current.set(t.senderId, {
        ...(userMapRef.current.get(t.senderId) ?? { displayName: t.senderUsername }),
        displayName: t.senderUsername,
        username: t.senderUsername,
      });
    }
  }, []);

  const handleReceipt = useCallback((_payload: unknown) => {
    // Receipt updates are routed by ChatPanel
  }, []);

  const handlePresence = useCallback((payload: unknown) => {
    const p = payload as PresenceUpdate;
    if (p?.userId == null) return;
    setOnlineUsers((prev) => {
      const next = new Set(prev);
      if (p.online) next.add(p.userId);
      else next.delete(p.userId);
      return next;
    });
  }, []);

  const handleCallEvent = useCallback(
    (payload: unknown) => {
      const event = payload as CallEvent;
      if (event?.type === 'REJECTED') {
        addToast('info', 'Call was declined');
      } else if (event?.type === 'CANCELLED') {
        addToast('info', 'Call was cancelled');
      } else if (event?.type === 'ENDED') {
        addToast('info', 'Call ended');
      }
    },
    [addToast]
  );

  const { send: stompSend, status: wsStatus } = useWebSocket({
    onMessage: handleMessage,
    onTyping: handleTypingEvent,
    onReceipt: handleReceipt,
    onPresence: handlePresence,
    onCall: handleCallEvent,
  });

  const handleSelectConversation = useCallback((conv: Conversation) => {
    const normalized = normalizeConversation(conv);
    if (normalized.directWithUser) {
      userMapRef.current.set(normalized.directWithUser.id, {
        displayName: normalized.directWithUser.displayName,
        username: normalized.directWithUser.username,
        avatarUrl: normalized.directWithUser.avatarUrl,
      });
    }
    setActiveConv(normalized);
  }, [normalizeConversation]);

  const getUserName = useCallback((userId: number): string => {
    return userMapRef.current.get(userId)?.displayName ?? `User ${userId}`;
  }, []);

  const getUserAvatar = useCallback((userId: number): string | undefined => {
    return userMapRef.current.get(userId)?.avatarUrl;
  }, []);

  // Show a single non-spammy toast when realtime status changes.
  const lastStatusRef = useRef(wsStatus);
  useEffect(() => {
    if (lastStatusRef.current === wsStatus) return;
    lastStatusRef.current = wsStatus;
    if (wsStatus === 'error') {
      addToast('error', 'Realtime connection lost — retrying...');
    }
  }, [wsStatus, addToast]);

  if (!user) {
    return null;
  }

  return (
    <CallProvider
      currentUserId={user.id}
      getUserName={getUserName}
      getUserAvatar={getUserAvatar}
      onError={(msg) => addToast('error', msg)}
    >
      <div className="messenger-page">
        <ChatPanel
          conversations={conversations}
          conversationsLoading={convLoading}
          activeConversation={activeConv}
          currentUserId={user.id}
          onlineUsers={onlineUsers}
          onSelectConversation={handleSelectConversation}
          onRefreshConversations={loadConversations}
          stompSend={stompSend}
          getUserName={getUserName}
          getUserAvatar={getUserAvatar}
          onError={(message) => addToast('error', message)}
        />
      </div>
      <ToastContainer toasts={toasts} onRemove={removeToast} />
    </CallProvider>
  );
}
