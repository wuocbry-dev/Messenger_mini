import React, { useState, useEffect, useCallback, useRef, useContext } from 'react';
import { createPortal } from 'react-dom';
import { SidebarSlotContext } from '../../../../app/SidebarSlot';
import { useRealtime } from '../../../../shared/realtime/RealtimeProvider';
import type { Conversation, Message, TypingIndicator, MessageReceipt } from '../../../../shared/types';
import { Sidebar } from '../Sidebar';
import { ChatHeader } from '../ChatHeader';
import { MessageList } from '../MessageList';
import { MessageInput } from '../MessageInput';
import { SharedMediaDrawer } from '../SharedMediaDrawer';
import { WelcomeShowcase } from '../WelcomeShowcase';
import { NewConversationModal } from '../NewConversationModal';
import { getConversationTitle, memberCount } from '../../utils/conversationName';
import { messageApi } from '../../api';
import { useCall } from '../../../call/components/CallProvider';
import './ChatPanel.css';

interface ChatPanelProps {
  conversations: Conversation[];
  conversationsLoading: boolean;
  activeConversation: Conversation | null;
  currentUserId: number;
  onlineUsers: Set<number>;
  onSelectConversation: (conv: Conversation) => void;
  onRefreshConversations: () => void;
  stompSend: (dest: string, body?: unknown) => void;
  getUserName: (userId: number) => string;
  getUserAvatar: (userId: number) => string | undefined;
  onError?: (message: string) => void;
}

export function ChatPanel({
  conversations,
  conversationsLoading,
  activeConversation,
  currentUserId,
  onlineUsers,
  onSelectConversation,
  onRefreshConversations,
  stompSend,
  getUserName,
  getUserAvatar,
  onError,
}: ChatPanelProps) {
  const [messages, setMessages] = useState<Message[]>([]);
  const [messagesLoading, setMessagesLoading] = useState(false);
  const [typingUsers, setTypingUsers] = useState<Map<number, string>>(new Map());
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const typingTimeout = useRef<Map<number, ReturnType<typeof setTimeout>>>(new Map());
  const { startCall, activeCall } = useCall();
  const slot = useContext(SidebarSlotContext);
  const { presence } = useRealtime();
  const [mediaDrawerOpen, setMediaDrawerOpen] = useState(false);
  const [showNewConv, setShowNewConv] = useState(false);

  // Load messages when active conversation changes
  useEffect(() => {
    if (!activeConversation) {
      setMessages([]);
      return;
    }
    let cancelled = false;
    setMessagesLoading(true);
    messageApi
      .list(activeConversation.id, { page: 0, size: 50 })
      .then((res) => {
        const pageMessages = res.messages ?? res.content ?? [];
        if (!cancelled) setMessages([...pageMessages].reverse());
      })
      .catch(console.error)
      .finally(() => {
        if (!cancelled) setMessagesLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [activeConversation?.id]);

  // Auto-close sidebar on mobile when a conversation is selected
  useEffect(() => {
    if (activeConversation) setSidebarOpen(false);
  }, [activeConversation?.id]);

  // Handle incoming messages from STOMP
  const handleIncomingMessage = useCallback(
    (payload: unknown) => {
      const msg = payload as Message;
      if (msg.conversationId === activeConversation?.id) {
        setMessages((prev) => {
          if (prev.some((m) => m.id === msg.id)) return prev;
          return [...prev, msg];
        });
        // Mark as delivered
        stompSend('/app/receipts.mark', { messageId: msg.id, state: 'DELIVERED' });
      }
      onRefreshConversations();
    },
    [activeConversation?.id, stompSend, onRefreshConversations]
  );

  // Handle typing indicators
  const handleTyping = useCallback(
    (payload: unknown) => {
      const typing = payload as TypingIndicator;
      if (typing.senderId === currentUserId) return;
      if (typing.conversationId !== activeConversation?.id) return;
      const existing = typingTimeout.current.get(typing.senderId);
      if (existing) clearTimeout(existing);
      if (typing.typing) {
        setTypingUsers((prev) => {
          const next = new Map(prev);
          next.set(typing.senderId, typing.senderUsername);
          return next;
        });
        const t = setTimeout(() => {
          setTypingUsers((prev) => {
            const next = new Map(prev);
            next.delete(typing.senderId);
            return next;
          });
        }, 5000);
        typingTimeout.current.set(typing.senderId, t);
      } else {
        setTypingUsers((prev) => {
          const next = new Map(prev);
          next.delete(typing.senderId);
          return next;
        });
      }
    },
    [currentUserId, activeConversation?.id]
  );

  // Handle receipt updates
  const handleReceipt = useCallback(
    (payload: unknown) => {
      const receipt = payload as MessageReceipt;
      if (receipt.conversationId !== activeConversation?.id) return;
      setMessages((prev) =>
        prev.map((m) =>
          m.id === receipt.messageId
            ? {
                ...m,
                receipts: [
                  ...m.receipts.filter((r) => r.userId !== receipt.userId),
                  {
                    messageId: receipt.messageId,
                    conversationId: receipt.conversationId,
                    userId: receipt.userId,
                    state: receipt.state,
                    deliveredAt:
                      receipt.state === 'DELIVERED' ? receipt.deliveredAt : m.receipts.find(r => r.userId === receipt.userId)?.deliveredAt,
                    readAt:
                      receipt.state === 'READ' ? receipt.readAt : m.receipts.find(r => r.userId === receipt.userId)?.readAt,
                  },
                ],
              }
            : m
        )
      );
    },
    [activeConversation?.id]
  );

  // Listen for STOMP events via the document-level bridge so that the
  // global useWebSocket hook can route messages to the right handlers.
  useEffect(() => {
    const onMessage = (e: Event) => handleIncomingMessage((e as CustomEvent).detail);
    const onTyping = (e: Event) => handleTyping((e as CustomEvent).detail);
    const onReceipt = (e: Event) => handleReceipt((e as CustomEvent).detail);
    document.addEventListener('chat:stomp-message', onMessage);
    document.addEventListener('chat:stomp-typing', onTyping);
    document.addEventListener('chat:stomp-receipt', onReceipt);
    return () => {
      document.removeEventListener('chat:stomp-message', onMessage);
      document.removeEventListener('chat:stomp-typing', onTyping);
      document.removeEventListener('chat:stomp-receipt', onReceipt);
    };
  }, [handleIncomingMessage, handleTyping, handleReceipt]);

  const handleSend = useCallback(
    (content: string) => {
      if (!activeConversation) return;
      const payload = { conversationId: activeConversation.id, type: 'TEXT', content };
      stompSend('/app/chat.send', payload);
    },
    [activeConversation, stompSend]
  );

  const handleTypingSignal = useCallback(
    (typing: boolean) => {
      if (!activeConversation) return;
      stompSend('/app/typing', { conversationId: activeConversation.id, typing });
    },
    [activeConversation, stompSend]
  );

  // Resolves false only when nothing was sent, so the composer keeps the tray
  // for a retry; after a partial send it reports the failure and clears it to
  // avoid re-sending the files that already went out.
  const handleAttach = useCallback(
    async (files: File[]): Promise<boolean> => {
      if (!activeConversation) return false;
      let sent = 0;
      try {
        for (const file of files) {
          const uploaded = await messageApi.uploadAttachment(file);
          const type = file.type.startsWith('image/') ? 'IMAGE' : 'FILE';
          const message = await messageApi.sendAttachment(activeConversation.id, uploaded.fileId, type);
          sent += 1;
          setMessages((prev) => (prev.some((m) => m.id === message.id) ? prev : [...prev, message]));
        }
        onRefreshConversations();
        return true;
      } catch (err) {
        const reason = err instanceof Error ? err.message : 'Failed to send file';
        onError?.(sent > 0 ? `Sent ${sent} of ${files.length} files. ${reason}` : reason);
        if (sent > 0) onRefreshConversations();
        return sent > 0;
      }
    },
    [activeConversation, onError, onRefreshConversations]
  );

  const otherUser = activeConversation?.directWithUser;
  const otherUserId = otherUser?.id;
  // typingUsers holds login handles (often emails); show the display name instead.
  const isGroup = activeConversation?.type === 'GROUP';
  const conversationTitle = activeConversation ? getConversationTitle(activeConversation, currentUserId) : '';
  // In a group several people can be typing at once.
  const typingNames = isGroup
    ? Array.from(typingUsers.keys()).filter((id) => id !== currentUserId).map((id) => getUserName(id))
    : [];
  const typingUser = isGroup
    ? typingNames.length > 0 ? typingNames.join(', ') : undefined
    : otherUserId && typingUsers.has(otherUserId) ? getUserName(otherUserId) : undefined;
  const otherUserName = otherUser ? getUserName(otherUser.id) : undefined;
  const otherUserAvatar = otherUser ? getUserAvatar(otherUser.id) : undefined;

  const placeCall = useCallback(
    (type: 'VOICE' | 'VIDEO') => {
      if (!activeConversation) return;
      if (activeConversation.type === 'GROUP') {
        // No single callee: the server rings every other member.
        void startCall(activeConversation.id, null, type, {
          group: true,
          title: getConversationTitle(activeConversation, currentUserId),
        });
      } else if (otherUserId) {
        void startCall(activeConversation.id, otherUserId, type);
      }
    },
    [activeConversation, otherUserId, startCall, currentUserId]
  );
  const handleStartCall = useCallback(() => placeCall('VOICE'), [placeCall]);
  const handleStartVideoCall = useCallback(() => placeCall('VIDEO'), [placeCall]);

  const sidebarEl = (
    <Sidebar
        currentUserId={currentUserId}
        conversations={conversations}
        activeConversationId={activeConversation?.id ?? null}
        onSelectConversation={onSelectConversation}
        onlineUsers={onlineUsers}
        loading={conversationsLoading}
        onRefresh={onRefreshConversations}
        open={sidebarOpen}
        onClose={() => setSidebarOpen(false)}
      />
  );

  return (
    <div className={`chat-panel ${activeConversation ? 'chat-panel--has-active' : ''}`}>
      {slot ? createPortal(sidebarEl, slot) : sidebarEl}

      <main className="chat-main">
        {activeConversation ? (
          <>
            <div className="chat-column">
              <ChatHeader
                conversation={activeConversation}
                currentUserId={currentUserId}
                online={otherUserId ? onlineUsers.has(otherUserId) : false}
                lastSeenAt={otherUserId ? presence.get(otherUserId)?.lastSeenAt : undefined}
                typing={!!typingUser}
                otherUserName={otherUserName}
                otherUserAvatar={otherUserAvatar}
                otherUserId={otherUserId}
                isGroup={isGroup}
                memberCount={activeConversation ? memberCount(activeConversation) : 0}
                displayTitle={conversationTitle}
                onCallClick={handleStartCall}
                onVideoCallClick={handleStartVideoCall}
                callInProgress={!!activeCall}
                onMenuClick={() => setSidebarOpen(true)}
                onMediaClick={() => setMediaDrawerOpen((o) => !o)}
              />
              <MessageList
                messages={messages}
                currentUserId={currentUserId}
                typingUser={typingUser}
                typingUserAvatar={isGroup ? undefined : otherUserAvatar}
                typingPlural={typingNames.length > 1}
                loading={messagesLoading}
                getUserAvatar={getUserAvatar}
              />
              <MessageInput
                conversationId={activeConversation.id}
                onSend={handleSend}
                onTyping={handleTypingSignal}
                onAttach={handleAttach}
              />
            </div>
            <SharedMediaDrawer
              conversation={activeConversation}
              open={mediaDrawerOpen}
              onClose={() => setMediaDrawerOpen(false)}
            />
          </>
        ) : (
          <div className="chat-empty">
            <WelcomeShowcase onStartChat={() => setShowNewConv(true)} />
          </div>
        )}
      </main>

      <NewConversationModal
        open={showNewConv}
        onClose={() => setShowNewConv(false)}
        onCreated={(conv) => {
          setShowNewConv(false);
          onRefreshConversations();
          onSelectConversation(conv as unknown as Conversation);
        }}
      />
    </div>
  );
}
