import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { ChatPanel } from '../features/chat/components/ChatPanel';
import { CallProvider, OPEN_CONVERSATION_EVENT } from '../features/call/components/CallProvider';
import { PendingCallLauncher } from './PendingCallLauncher';
import { useRealtime } from '../shared/realtime/RealtimeProvider';
import { conversationApi } from '../features/chat/api/conversationApi';
import { authApi } from '../features/auth/api/authApi';
import { clearAuth } from '../features/auth/store/authStore';
import { ApiRequestError } from '../shared/api';
import { ToastContainer, type ToastData } from '../shared/components/ui/Toast';
import { useIsMobile } from '../shared/hooks/useIsMobile';
import type {
  Conversation,
  Message,
  TypingIndicator,
  MessageReceipt,
  CallEvent,
} from '../shared/types';

/** Query param holding the open chat, so phones get Back/Forward and deep links. */
const CONVERSATION_PARAM = 'conversation';

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
  const location = useLocation();
  const isMobile = useIsMobile();
  const [user, setUser] = useState<UserSummary | null>(null);
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [convLoading, setConvLoading] = useState(true);
  const [activeConv, setActiveConv] = useState<Conversation | null>(null);
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

  // Load conversations. Refreshes (triggered by every incoming/sent message)
  // are silent: no skeleton, details are cached, and live fields set by the
  // realtime handlers (last message, unread count) are carried over.
  const detailCache = useRef<Map<number, { conv: Conversation; at: number }>>(new Map());
  const loadedOnce = useRef(false);
  const DETAIL_TTL_MS = 10 * 60 * 1000; // avatar URLs are presigned and expire

  const loadConversations = useCallback(() => {
    if (!user) return;
    if (!loadedOnce.current) setConvLoading(true);
    conversationApi
      .list()
      .then(async (items) => {
        const now = Date.now();
        const details = await Promise.all(
          items.map(async (item) => {
            const hit = detailCache.current.get(item.id);
            if (hit && now - hit.at < DETAIL_TTL_MS) {
              return { ...hit.conv, updatedAt: item.updatedAt, title: item.title ?? hit.conv.title };
            }
            const detail = await conversationApi.get(item.id).catch(() => item);
            const normalizedDetail = normalizeConversation(detail);
            if (normalizedDetail.members) {
              detailCache.current.set(item.id, { conv: normalizedDetail, at: now });
            }
            return normalizedDetail;
          })
        );
        const normalized = details.map(normalizeConversation);
        setConversations((prev) => {
          const live = new Map(prev.map((c) => [c.id, c]));
          return normalized.map((c) => {
            const old = live.get(c.id);
            return old
              ? {
                  ...c,
                  lastMessage: old.lastMessage ?? c.lastMessage,
                  lastMessageAt: old.lastMessageAt ?? c.lastMessageAt,
                  unreadCount: old.unreadCount ?? c.unreadCount,
                }
              : c;
          });
        });
        normalized.forEach((c) => {
          // Everyone in a conversation, so group messages show real names and photos.
          c.members?.forEach((m) => {
            if (m.userId === user.id) return;
            userMapRef.current.set(m.userId, {
              displayName: m.displayName,
              username: m.username,
              avatarUrl: m.avatarUrl,
            });
          });
          if (c.directWithUser) {
            userMapRef.current.set(c.directWithUser.id, {
              displayName: c.directWithUser.displayName,
              username: c.directWithUser.username,
              avatarUrl: c.directWithUser.avatarUrl,
            });
          }
        });
        loadedOnce.current = true;
      })
      .catch((err) => addToast('error', err.message ?? 'Failed to load conversations'))
      .finally(() => setConvLoading(false));
  }, [user, addToast, normalizeConversation]);

  useEffect(() => {
    void loadConversations();
  }, [loadConversations]);

  // Avatar URLs in conversation details expire; refresh a bit after the cache
  // window so idle sessions do not end up with broken pictures.
  useEffect(() => {
    const timer = setInterval(() => void loadConversations(), DETAIL_TTL_MS + 60_000);
    return () => clearInterval(timer);
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
      // senderUsername is the login handle (often the email), not the display
      // name. Never overwrite a known display name with it.
      const known = userMapRef.current.get(t.senderId);
      userMapRef.current.set(t.senderId, {
        ...known,
        displayName: known?.displayName ?? t.senderUsername,
        username: known?.username ?? t.senderUsername,
      });
    }
  }, []);

  const handleReceipt = useCallback((_payload: unknown) => {
    // Receipt updates are routed by ChatPanel
  }, []);

  const handleCallEvent = useCallback(
    (payload: unknown) => {
      const event = payload as CallEvent;
      // Being added to a call can create a group chat (or add me to one).
      if (
        event?.type === 'INCOMING' ||
        event?.type === 'CONVERSATION_CHANGED' ||
        event?.type === 'PARTICIPANT_INVITED'
      ) {
        void loadConversations();
      }
      if (event?.type === 'REJECTED') {
        addToast('info', 'Call was declined');
      } else if (event?.type === 'CANCELLED') {
        addToast('info', 'Call was cancelled');
      } else if (event?.type === 'ENDED') {
        addToast('info', 'Call ended');
      }
    },
    [addToast, loadConversations]
  );

  // The connection lives in RealtimeProvider (so it stays up on every page);
  // this page listens to the events it dispatches.
  const { send: stompSend, status: wsStatus, presence } = useRealtime();

  const onlineUsers = useMemo(() => {
    const ids = new Set<number>();
    presence.forEach((info, id) => {
      if (info.online) ids.add(id);
    });
    return ids;
  }, [presence]);

  useEffect(() => {
    const bind = (name: string, fn: (payload: unknown) => void) => {
      const listener = (e: Event) => fn((e as CustomEvent).detail);
      document.addEventListener(name, listener);
      return () => document.removeEventListener(name, listener);
    };
    const unbinds = [
      bind('chat:stomp-message', handleMessage),
      bind('chat:stomp-typing', handleTypingEvent),
      bind('chat:stomp-receipt', handleReceipt),
      bind('messenger:call-event', handleCallEvent),
    ];
    return () => unbinds.forEach((off) => off());
  }, [handleMessage, handleTypingEvent, handleReceipt, handleCallEvent]);

  const showConversation = useCallback((conv: Conversation) => {
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

  // The open chat is mirrored in `/?conversation=<id>`. Phones push a history
  // entry (the list is the screen "behind" the chat, so Back returns to it);
  // desktop replaces the entry so its history behaves as before.
  const handleSelectConversation = useCallback((conv: Conversation, opts?: { replace?: boolean }) => {
    showConversation(conv);
    const current = new URLSearchParams(window.location.search).get(CONVERSATION_PARAM);
    // A replace still runs when the URL already matches: it clears one-shot router state.
    if (current === String(conv.id) && !opts?.replace) return;
    const to = { pathname: '/', search: `?${CONVERSATION_PARAM}=${conv.id}` };
    if (isMobile && !opts?.replace) {
      navigate(to, { state: { fromChatList: true } });
    } else {
      navigate(to, { replace: true });
    }
  }, [showConversation, isMobile, navigate]);

  // URL -> selection: deep links, browser Back/Forward and resizes.
  const paramValue = new URLSearchParams(location.search).get(CONVERSATION_PARAM);
  const paramId = paramValue ? Number(paramValue) : null;
  const prevParamId = useRef(paramId);
  useEffect(() => {
    const hadParam = prevParamId.current != null;
    prevParamId.current = paramId;
    if (paramId == null) {
      // Back from a chat on a phone returns to the list. Desktop keeps the
      // open chat, as it always has, when the URL loses the param.
      if (isMobile && hadParam) setActiveConv(null);
      return;
    }
    if (activeConv?.id === paramId) return;
    const conv = conversations.find((c) => c.id === paramId);
    if (conv) {
      showConversation(conv);
    } else if (!convLoading && loadedOnce.current) {
      // Unknown or inaccessible conversation: fall back to the plain list.
      navigate('/', { replace: true });
    }
  }, [paramId, activeConv?.id, conversations, convLoading, isMobile, showConversation, navigate]);

  const handleBack = useCallback(() => {
    if ((location.state as { fromChatList?: boolean } | null)?.fromChatList) {
      navigate(-1); // pops back to the list entry; the effect above clears the chat
    } else {
      // Entered directly (deep link, Users page, call): there is no list entry behind.
      setActiveConv(null);
      navigate('/', { replace: true });
    }
  }, [location.state, navigate]);

  // Open a conversation asked for by the call UI (e.g. the group chat created
  // when someone is added to a call). It is selected once it is in the list.
  const pendingOpenRef = useRef<number | null>(null);
  const [openTick, setOpenTick] = useState(0);
  useEffect(() => {
    const onOpen = (e: Event) => {
      pendingOpenRef.current = (e as CustomEvent<number>).detail;
      void loadConversations();
      setOpenTick((t) => t + 1);
    };
    document.addEventListener(OPEN_CONVERSATION_EVENT, onOpen);
    return () => document.removeEventListener(OPEN_CONVERSATION_EVENT, onOpen);
  }, [loadConversations]);

  useEffect(() => {
    const id = pendingOpenRef.current;
    if (id == null) return;
    const conv = conversations.find((c) => c.id === id);
    if (conv) {
      pendingOpenRef.current = null;
      handleSelectConversation(conv);
    }
  }, [conversations, handleSelectConversation, openTick]);

  // Open a conversation requested by another page (e.g. the user directory).
  useEffect(() => {
    const id = (location.state as { openConversationId?: number } | null)?.openConversationId;
    if (!id || convLoading) return;
    const conv = conversations.find((c) => c.id === id);
    if (conv) {
      // Replaces the entry, which also drops the one-shot router state.
      handleSelectConversation(conv, { replace: true });
    }
  }, [location, convLoading, conversations, handleSelectConversation]);

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
      <PendingCallLauncher />
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
          mobile={isMobile}
          onBack={handleBack}
        />
      </div>
      <ToastContainer toasts={toasts} onRemove={removeToast} />
    </CallProvider>
  );
}
