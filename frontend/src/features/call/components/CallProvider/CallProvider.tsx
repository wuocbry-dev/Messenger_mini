/**
 * CallProvider — owns the call state for 1-to-1 and group calls and wires the
 * server's call events to the call UI.
 *
 * The server sends the full participant list with every event, so the UI
 * state is rebuilt from the latest snapshot rather than inferred from event
 * order. Events arrive as DOM CustomEvents (`messenger:call-event`) dispatched
 * by the websocket hook, which keeps this provider independent of the socket.
 */
import React, { createContext, useContext, useState, useCallback, useRef, useEffect, useMemo } from 'react';
import type { CallEvent, CallParticipant, CallResponse } from '../../../../shared/types';
import { callApi } from '../../api/callApi';
import { useAgora, type CallMedia } from '../../hooks/useAgora';
import { IncomingCallModal } from '../CallModal';
import { ActiveCall } from '../ActiveCall';

interface ActiveCallInfo {
  id: number;
  conversationId: number;
  channelName: string;
  isInitiator: boolean;
  callType: CallMedia;
  participants: CallParticipant[];
  /** Name of the group chat the call belongs to, when known. */
  title?: string;
  /** The call already lives in a group chat (so adding people does not create one). */
  inGroupChat: boolean;
}

interface StartCallOptions {
  /** Name of the group chat (shown as the call's title). */
  title?: string;
  /** The conversation is a group chat. */
  group?: boolean;
}

interface CallContextValue {
  incomingCall: CallEvent | null;
  activeCall: ActiveCallInfo | null;
  /** Pass `calleeId = null` to ring everyone in a group conversation. */
  startCall: (
    conversationId: number,
    calleeId: number | null,
    callType?: CallMedia,
    options?: StartCallOptions
  ) => Promise<void>;
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
export const OPEN_CONVERSATION_EVENT = 'messenger:open-conversation';

const TERMINAL: ReadonlySet<CallEvent['type']> = new Set(['REJECTED', 'CANCELLED', 'MISSED', 'ENDED']);

function fromResponse(call: CallResponse, isInitiator: boolean, extra?: StartCallOptions): ActiveCallInfo {
  return {
    id: call.id,
    conversationId: call.conversationId,
    channelName: call.agoraChannelName,
    isInitiator,
    callType: call.callType,
    participants: call.participants,
    title: extra?.title,
    inGroupChat: extra?.group ?? call.participants.length > 2,
  };
}

function openConversation(id: number): void {
  document.dispatchEvent(new CustomEvent<number>(OPEN_CONVERSATION_EVENT, { detail: id }));
}

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
  const {
    state: agoraState,
    join,
    leave,
    toggleMute,
    toggleCamera,
    enableCamera,
    toggleSpeaker,
    toggleScreenShare,
    listCameras,
    switchCamera,
  } = useAgora();
  const incomingCallRef = useRef<CallEvent | null>(null);
  incomingCallRef.current = incomingCall;
  const activeCallRef = useRef<ActiveCallInfo | null>(null);
  activeCallRef.current = activeCall;

  const handleError = useCallback(
    (err: unknown) => {
      const message = err instanceof Error ? err.message : 'Call error';
      onError?.(message);
    },
    [onError]
  );

  /** Nobody but me has joined yet, and someone is still being rung. */
  const ringing = useMemo(() => {
    if (!activeCall) return false;
    const ps = activeCall.participants;
    return (
      ps.some((p) => p.state === 'INVITED') &&
      !ps.some((p) => p.userId !== currentUserId && p.state === 'JOINED')
    );
  }, [activeCall, currentUserId]);

  const joinChannel = useCallback(
    async (callId: number, media: CallMedia) => {
      const token = await callApi.getToken(callId);
      await join(token.appId, token.agoraChannelName, token.uid, token.token, media);
    },
    [join]
  );

  const startCall = useCallback(
    async (
      conversationId: number,
      calleeId: number | null,
      callType: CallMedia = 'VOICE',
      options?: StartCallOptions
    ) => {
      try {
        const call = await callApi.start(conversationId, calleeId, callType);
        setActiveCall(fromResponse(call, true, options));
        await joinChannel(call.id, callType);
      } catch (err) {
        handleError(err);
        setActiveCall(null);
      }
    },
    [joinChannel, handleError]
  );

  const acceptIncoming = useCallback(async () => {
    const incoming = incomingCallRef.current;
    if (!incoming) return;
    setIncomingCall(null);
    try {
      const call = await callApi.accept(incoming.callId);
      setActiveCall(fromResponse(call, false));
      try {
        await joinChannel(call.id, call.callType);
      } catch (err) {
        // Could not get into the channel: leave the call so others are not left waiting.
        void callApi.end(call.id).catch(() => {});
        throw err;
      }
    } catch (err) {
      handleError(err);
      setActiveCall(null);
    }
  }, [joinChannel, handleError]);

  const rejectIncoming = useCallback(async () => {
    const incoming = incomingCallRef.current;
    if (!incoming) return;
    setIncomingCall(null);
    try {
      await callApi.reject(incoming.callId);
    } catch (err) {
      handleError(err);
    }
  }, [handleError]);

  const endActiveCall = useCallback(async () => {
    const call = activeCallRef.current;
    const stillRinging =
      !!call &&
      call.participants.some((p) => p.state === 'INVITED') &&
      !call.participants.some((p) => p.userId !== currentUserId && p.state === 'JOINED');
    setActiveCall(null);
    if (call) {
      try {
        // Caller still ringing -> cancel; otherwise leave the running call.
        await (stillRinging && call.isInitiator ? callApi.cancel(call.id) : callApi.end(call.id));
      } catch (err) {
        handleError(err);
      }
    }
    await leave();
  }, [leave, handleError, currentUserId]);

  /** Rings more people; errors are shown by the dialog that asked. */
  const inviteToCall = useCallback(async (userIds: number[]) => {
    const call = activeCallRef.current;
    if (!call) return;
    const res = await callApi.invite(call.id, userIds);
    setActiveCall((prev) =>
      prev && prev.id === res.id
        ? {
            ...prev,
            participants: res.participants,
            conversationId: res.conversationId,
            inGroupChat: true,
          }
        : prev
    );
    if (res.conversationId !== call.conversationId) openConversation(res.conversationId);
  }, []);

  /** Voice -> video: start my camera, then tell everyone the call is now video. */
  const upgradeToVideo = useCallback(async () => {
    const call = activeCallRef.current;
    if (!call) return;
    const ok = await enableCamera();
    if (!ok) return;
    try {
      const res = await callApi.upgradeToVideo(call.id);
      setActiveCall((prev) =>
        prev && prev.id === res.id ? { ...prev, callType: 'VIDEO', participants: res.participants } : prev
      );
    } catch (err) {
      handleError(err);
    }
  }, [enableCamera, handleError]);

  // Start the duration clock when someone else's audio first connects.
  const [connectedAt, setConnectedAt] = useState<number | null>(null);
  useEffect(() => {
    if (!activeCall) setConnectedAt(null);
    else if (agoraState.remoteJoined) setConnectedAt((prev) => prev ?? Date.now());
  }, [activeCall, agoraState.remoteJoined]);

  // Bridge: apply the server's call events.
  useEffect(() => {
    const handler = (e: Event) => {
      const event = (e as CustomEvent<CallEvent>).detail;
      if (!event?.type) return;

      const active = activeCallRef.current;
      const incoming = incomingCallRef.current;

      // Someone is ringing me (a new call, or I was added to a running one).
      if (event.type === 'INCOMING') {
        if (event.actorId !== currentUserId && !active && !incoming) {
          setIncomingCall(event);
        }
        return;
      }

      // Updates about the call that is ringing me.
      if (incoming && incoming.callId === event.callId) {
        const answeredElsewhere =
          event.actorId === currentUserId &&
          (event.type === 'ACCEPTED' || event.type === 'PARTICIPANT_JOINED');
        if (TERMINAL.has(event.type) || answeredElsewhere) {
          setIncomingCall(null);
        } else {
          setIncomingCall({ ...event, type: 'INCOMING' });
        }
        return;
      }

      // Updates about the call I am in.
      if (active && active.id === event.callId) {
        if (TERMINAL.has(event.type)) {
          setActiveCall(null);
          void leave();
          if (event.type === 'MISSED') onError?.('No answer');
          return;
        }
        setActiveCall({
          ...active,
          callType: event.callType,
          conversationId: event.conversationId,
          participants: event.participants,
          inGroupChat: active.inGroupChat || event.type === 'CONVERSATION_CHANGED',
        });
        if (event.type === 'CONVERSATION_CHANGED') openConversation(event.conversationId);
      }
    };
    document.addEventListener(CALL_EVENT_NAME, handler);
    return () => document.removeEventListener(CALL_EVENT_NAME, handler);
  }, [currentUserId, leave, onError]);

  const caller = incomingCall?.participants.find((p) => p.userId === incomingCall.callerId);
  const incomingName = caller?.displayName ?? (incomingCall ? getUserName(incomingCall.callerId) : '');
  const incomingAvatar =
    caller?.avatarUrl ?? (incomingCall ? getUserAvatar(incomingCall.callerId) : undefined) ?? undefined;
  const incomingPeople = incomingCall
    ? incomingCall.participants.filter((p) => p.state === 'JOINED' || p.state === 'INVITED').length
    : 0;

  return (
    <CallContext.Provider
      value={{ incomingCall, activeCall, startCall, acceptIncoming, rejectIncoming, endActiveCall }}
    >
      {children}

      <IncomingCallModal
        call={incomingCall}
        onAccept={() => void acceptIncoming()}
        onReject={() => void rejectIncoming()}
        callerName={incomingName}
        callerAvatar={incomingAvatar}
        groupSize={incomingPeople}
      />

      {activeCall && (
        <ActiveCall
          title={activeCall.title}
          participants={activeCall.participants}
          currentUserId={currentUserId}
          video={activeCall.callType === 'VIDEO'}
          connectedAt={connectedAt}
          ringing={ringing}
          state={agoraState}
          inviteCreatesGroup={!activeCall.inGroupChat}
          onToggleMute={() => void toggleMute()}
          onToggleCamera={() => void toggleCamera()}
          onToggleSpeaker={toggleSpeaker}
          onToggleShare={() => void toggleScreenShare()}
          onUpgradeToVideo={() => void upgradeToVideo()}
          onInvite={inviteToCall}
          listCameras={listCameras}
          onSwitchCamera={(id) => void switchCamera(id)}
          onEnd={() => void endActiveCall()}
        />
      )}
    </CallContext.Provider>
  );
}
