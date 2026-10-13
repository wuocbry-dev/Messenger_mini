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
import { useAgora, type CallMedia } from '../../hooks/useAgora';
import { IncomingCallModal } from '../CallModal';
import { ActiveCall } from '../ActiveCall';

interface ActiveCallInfo {
  id: number;
  channelName: string;
  peerName: string;
  peerAvatar?: string;
  isInitiator: boolean;
  startedAt: number;
  callType: CallMedia;
}

interface CallContextValue {
  incomingCall: CallEvent | null;
  activeCall: ActiveCallInfo | null;
  startCall: (conversationId: number, calleeId: number, callType?: CallMedia) => Promise<void>;
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
  const { state: agoraState, join, leave, toggleMute, toggleCamera } = useAgora();
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
    async (conversationId: number, calleeId: number, callType: CallMedia = 'VOICE') => {
      try {
        const call = await callApi.start(conversationId, calleeId, callType);
        setActiveCall({
          id: call.id,
          channelName: call.agoraChannelName,
          peerName: getUserName(calleeId),
          peerAvatar: getUserAvatar(calleeId),
          isInitiator: true,
          startedAt: Date.now(),
          callType,
        });
        setOutgoing(true);
        const token = await callApi.getToken(call.id);
        await join(token.appId, token.agoraChannelName, token.uid, token.token, callType);
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
    const ringing = outgoingRef.current;
    setActiveCall(null);
    setOutgoing(false);
    if (call) {
      try {
        // Caller still ringing -> cancel; otherwise the call was accepted -> end.
        await (ringing ? callApi.cancel(call.id) : callApi.end(call.id));
      } catch (err) {
        handleError(err);
      }
    }
    await leave();
  }, [leave, handleError]);

  // Start the duration clock when the peer's audio first connects.
  const [connectedAt, setConnectedAt] = useState<number | null>(null);
  useEffect(() => {
    if (!activeCall) setConnectedAt(null);
    else if (agoraState.remoteJoined) setConnectedAt((prev) => prev ?? Date.now());
  }, [activeCall, agoraState.remoteJoined]);

  // Bridge: subscribe to STOMP call events and update call state.
  useEffect(() => {
    const handler = (e: Event) => {
      const event = (e as CustomEvent<CallEvent>).detail;
      if (!event?.type) return;
      switch (event.type) {
        case 'INCOMING':
          // The caller also receives INCOMING (multi-device sync); never ring ourselves.
          if (event.callerId !== currentUserId && !activeCallRef.current) {
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
              callType: event.callType,
            });
            callApi
              .getToken(event.callId)
              .then((token) =>
                join(token.appId, token.agoraChannelName, token.uid, token.token, event.callType)
              )
              .catch(handleError);
          }
          break;
        case 'REJECTED':
          setOutgoing(false);
          setActiveCall(null);
          void leave();
          break;
        case 'CANCELLED':
          // Callee side: caller hung up before answering.
          setIncomingCall((cur) => (cur?.callId === event.callId ? null : cur));
          break;
        case 'MISSED':
          // Ring timeout: callee's prompt closes, caller's outgoing call ends.
          setIncomingCall((cur) => (cur?.callId === event.callId ? null : cur));
          if (activeCallRef.current?.id === event.callId) {
            setOutgoing(false);
            setActiveCall(null);
            void leave();
            onError?.('No answer');
          }
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
  }, [currentUserId, join, leave, getUserName, getUserAvatar, handleError, onError]);

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
          peerName={activeCall.peerName}
          peerAvatar={activeCall.peerAvatar}
          video={activeCall.callType === 'VIDEO'}
          connectedAt={connectedAt}
          ringing={outgoing}
          state={agoraState}
          onToggleMute={() => void toggleMute()}
          onToggleCamera={() => void toggleCamera()}
          onEnd={() => void endActiveCall()}
        />
      )}
    </CallContext.Provider>
  );
}
