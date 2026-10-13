import React, { useState, useEffect, useCallback, useRef, useContext } from 'react';
import { createPortal } from 'react-dom';
import { SidebarSlotContext } from '../../../../app/SidebarSlot';
import type { Conversation, Message, TypingIndicator, MessageReceipt } from '../../../../shared/types';
import { Sidebar } from '../Sidebar';
import { ChatHeader } from '../ChatHeader';
import { MessageList } from '../MessageList';
import { MessageInput } from '../MessageInput';
import { SharedMediaDrawer } from '../SharedMediaDrawer';
import { EmptyState } from '../../../../shared/components/ui';
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
  const [mediaDrawerOpen, setMediaDrawerOpen] = useState(false);

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
  const typingUser = otherUserId && typingUsers.has(otherUserId) ? getUserName(otherUserId) : undefined;
  const otherUserName = otherUser ? getUserName(otherUser.id) : undefined;
  const otherUserAvatar = otherUser ? getUserAvatar(otherUser.id) : undefined;

  const handleStartCall = useCallback(() => {
    if (otherUserId) {
      void startCall(activeConversation!.id, otherUserId);
    }
  }, [otherUserId, activeConversation, startCall]);

  const handleStartVideoCall = useCallback(() => {
    if (otherUserId) {
      void startCall(activeConversation!.id, otherUserId, 'VIDEO');
    }
  }, [otherUserId, activeConversation, startCall]);

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
            <ChatHeader
              conversation={activeConversation}
              currentUserId={currentUserId}
              online={otherUserId ? onlineUsers.has(otherUserId) : false}
              typing={!!typingUser}
              otherUserName={otherUserName}
              otherUserAvatar={otherUserAvatar}
              otherUserId={otherUserId}
              onCallClick={handleStartCall}
              onVideoCallClick={handleStartVideoCall}
              callInProgress={!!activeCall}
              onMenuClick={() => setSidebarOpen(true)}
              onMediaClick={() => setMediaDrawerOpen(true)}
            />
            <MessageList
              messages={messages}
              currentUserId={currentUserId}
              typingUser={typingUser}
              typingUserAvatar={otherUserAvatar}
              loading={messagesLoading}
              getUserAvatar={getUserAvatar}
            />
            <MessageInput
              conversationId={activeConversation.id}
              onSend={handleSend}
              onTyping={handleTypingSignal}
              onAttach={handleAttach}
            />
            <SharedMediaDrawer
              conversation={activeConversation}
              open={mediaDrawerOpen}
              onClose={() => setMediaDrawerOpen(false)}
            />
          </>
        ) : (
          <div className="chat-empty">
            <EmptyState
              icon={
                <svg
                  width="48"
                  height="48"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="1.5"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
                </svg>
              }
              title="Welcome to Messenger Mini"
              description="Pick a conversation from the list, or start a new one to begin chatting"
            />
          </div>
        )}
      </main>
    </div>
  );
}
