/**
 * CallProvider — manages call state machine and wires STOMP events to call UI.
 * Renders IncomingCallModal when there's an incoming call,
 * and ActiveCall floating panel when in a call.
 *
 * Call events are received via a DOM CustomEvent `messenger:call-event`
 * dispatched by the useWebSocket hook. This decouples the provider from
 * the WebSocket layer.
 */
import React, { createContext, useContext, useState, useCallback, useRef, useEffect } from 'react';
import type { CallEvent } from '../../../../shared/types';
import { callApi } from '../../api/callApi';
import { useAgora } from '../../hooks/useAgora';
import { IncomingCallModal } from '../CallModal';
import { ActiveCall } from '../ActiveCall';

interface ActiveCallInfo {
  id: number;
  channelName: string;
  peerName: string;
  peerAvatar?: string;
  isInitiator: boolean;
  startedAt: number;
}

interface CallContextValue {
  incomingCall: CallEvent | null;
  activeCall: ActiveCallInfo | null;
  startCall: (conversationId: number, calleeId: number) => Promise<void>;
  acceptIncoming: () => Promise<void>;
  rejectIncoming: () => Promise<void>;
  endActiveCall: () => Promise<void>;
}

const CallContext = createContext<CallContextValue>({
  incomingCall: null,
  activeCall: null,
  startCall: async () => {},
  acceptIncoming: async () => {},
  rejectIncoming: async () => {},
  endActiveCall: async () => {},
});

export function useCall(): CallContextValue {
  return useContext(CallContext);
}

const CALL_EVENT_NAME = 'messenger:call-event';

interface Props {
  currentUserId: number;
  getUserName: (userId: number) => string;
  getUserAvatar: (userId: number) => string | undefined;
  onError?: (message: string) => void;
  children: React.ReactNode;
}

export function CallProvider({ currentUserId, getUserName, getUserAvatar, onError, children }: Props) {
  const [incomingCall, setIncomingCall] = useState<CallEvent | null>(null);
  const [activeCall, setActiveCall] = useState<ActiveCallInfo | null>(null);
  const [outgoing, setOutgoing] = useState(false);
  const { state: agoraState, join, leave } = useAgora();
  const incomingCallRef = useRef<CallEvent | null>(null);
  incomingCallRef.current = incomingCall;
  const activeCallRef = useRef<ActiveCallInfo | null>(null);
  activeCallRef.current = activeCall;
  const outgoingRef = useRef(false);
  outgoingRef.current = outgoing;

  const handleError = useCallback(
    (err: unknown) => {
      const message = err instanceof Error ? err.message : 'Call error';
      onError?.(message);
    },
    [onError]
  );

  const startCall = useCallback(
    async (conversationId: number, calleeId: number) => {
      try {
        const call = await callApi.start(conversationId, calleeId);
        setActiveCall({
          id: call.id,
          channelName: call.agoraChannelName,
          peerName: getUserName(calleeId),
          peerAvatar: getUserAvatar(calleeId),
          isInitiator: true,
          startedAt: Date.now(),
        });
        setOutgoing(true);
        const token = await callApi.getToken(call.id);
        await join(token.appId, token.agoraChannelName, currentUserId, token.token);
      } catch (err) {
        handleError(err);
        setActiveCall(null);
        setOutgoing(false);
      }
    },
    [currentUserId, join, getUserName, getUserAvatar, handleError]
  );

  const acceptIncoming = useCallback(async () => {
    const call = incomingCallRef.current;
    if (!call) return;
    setIncomingCall(null);
    try {
      await callApi.accept(call.callId);
    } catch (err) {
      handleError(err);
    }
  }, [handleError]);

  const rejectIncoming = useCallback(async () => {
    const call = incomingCallRef.current;
    if (!call) return;
    setIncomingCall(null);
    try {
      await callApi.reject(call.callId);
    } catch (err) {
      handleError(err);
    }
  }, [handleError]);

  const endActiveCall = useCallback(async () => {
    const call = activeCallRef.current;
    if (call) {
      try {
        await callApi.end(call.id);
      } catch (err) {
        handleError(err);
      }
    }
    await leave();
    setActiveCall(null);
    setOutgoing(false);
  }, [leave, handleError]);

  // Bridge: subscribe to STOMP call events and update call state.
  useEffect(() => {
    const handler = (e: Event) => {
      const event = (e as CustomEvent<CallEvent>).detail;
      if (!event?.type) return;
      switch (event.type) {
        case 'INCOMING':
          if (!activeCallRef.current) {
            setIncomingCall(event);
          }
          break;
        case 'ACCEPTED':
          if (outgoingRef.current) {
            setOutgoing(false);
          } else {
            setIncomingCall(null);
            setActiveCall({
              id: event.callId,
              channelName: event.agoraChannelName,
              peerName: getUserName(event.callerId),
              peerAvatar: getUserAvatar(event.callerId),
              isInitiator: false,
              startedAt: Date.now(),
            });
            callApi
              .getToken(event.callId)
              .then((token) =>
                join(token.appId, token.agoraChannelName, currentUserId, token.token)
              )
              .catch(handleError);
          }
          break;
        case 'REJECTED':
        case 'CANCELLED':
          setOutgoing(false);
          setActiveCall(null);
          break;
        case 'ENDED':
          setActiveCall(null);
          setOutgoing(false);
          void leave();
          break;
      }
    };
    document.addEventListener(CALL_EVENT_NAME, handler);
    return () => document.removeEventListener(CALL_EVENT_NAME, handler);
  }, [currentUserId, join, leave, getUserName, getUserAvatar, handleError]);

  return (
    <CallContext.Provider
      value={{ incomingCall, activeCall, startCall, acceptIncoming, rejectIncoming, endActiveCall }}
    >
      {children}

      <IncomingCallModal
        call={incomingCall}
        onAccept={() => void acceptIncoming()}
        onReject={() => void rejectIncoming()}
        callerName={incomingCall ? getUserName(incomingCall.callerId) : ''}
        callerAvatar={incomingCall ? getUserAvatar(incomingCall.callerId) : undefined}
      />

      {activeCall && (
        <ActiveCall
          callId={activeCall.id}
          channelName={activeCall.channelName}
          peerName={activeCall.peerName}
          peerAvatar={activeCall.peerAvatar}
          isInitiator={activeCall.isInitiator}
          startedAt={activeCall.startedAt}
          onEnd={() => void endActiveCall()}
        />
      )}
    </CallContext.Provider>
  );
}
