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
  const [, setOutgoing] = useState(false);
  const { state: agoraState, join, leave } = useAgora();
  const incomingCallRef = useRef<CallEvent | null>(null);
  incomingCallRef.current = incomingCall;
  const activeCallRef = useRef<ActiveCallInfo | null>(null);
  activeCallRef.current = activeCall;

  const handleError = useCallback(
    (err: unknown) => {
      let message: string;
      if (err && typeof err === 'object' && 'status' in err) {
        const status = (err as { status: number }).status;
        if (status === 409) {
          message = 'A call is already active. Please end the current call first.';
        } else if (status === 403) {
          message = 'You are not allowed to perform this action.';
        } else if (status === 404) {
          message = 'Call not found.';
        } else {
          message = err instanceof Error ? err.message : 'Call error';
        }
      } else {
        message = err instanceof Error ? err.message : 'Call error';
      }
      onError?.(message);
    },
    [onError]
  );

  const startCall = useCallback(
    async (conversationId: number, calleeId: number) => {
      let createdCallId: number | null = null;
      try {
        const call = await callApi.start(conversationId, calleeId);
        createdCallId = call.id;
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
        // Call was created but token/Agora join failed: cancel it so the
        // callee is not left ringing for a call the caller can't join.
        if (createdCallId !== null) {
          const callId = createdCallId;
          // cancel only works while RINGING; if already accepted, end it.
          void callApi.cancel(callId).catch(() => callApi.end(callId).catch(() => {}));
          void leave();
        }
        setActiveCall(null);
        setOutgoing(false);
        // If conflict, also cancel any pending state
        if (err && typeof err === 'object' && 'status' in err && (err as { status: number }).status === 409) {
          setIncomingCall(null);
        }
      }
    },
    [currentUserId, join, leave, getUserName, getUserAvatar, handleError]
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
      // The backend also sends INCOMING/ACCEPTED to the caller (cross-device
      // sync), so decide the role from the event, not from local UI state.
      const isCaller = event.callerId === currentUserId;
      switch (event.type) {
        case 'INCOMING':
          if (!isCaller && !activeCallRef.current) {
            setIncomingCall(event);
          }
          break;
        case 'ACCEPTED':
          if (isCaller) {
            setOutgoing(false);
          } else if (activeCallRef.current?.id === event.callId) {
            // Duplicate ACCEPTED for a call we already joined.
            break;
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
              .catch((err) => {
                handleError(err);
                // Don't leave an ACCEPTED call behind that nobody is in.
                void callApi.end(event.callId).catch(() => {});
                void leave();
                setActiveCall(null);
              });
          }
          break;
        case 'REJECTED':
        case 'CANCELLED':
          setIncomingCall((current) => (current?.callId === event.callId ? null : current));
          setOutgoing(false);
          setActiveCall(null);
          void leave();
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
