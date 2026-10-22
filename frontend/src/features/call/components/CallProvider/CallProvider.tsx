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
import { createPortal } from 'react-dom';
import { useRealtime } from '../../../../shared/realtime/RealtimeProvider';
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
  /** Calls in progress in my conversations that I can join (e.g. a group call I missed). */
  joinableCalls: CallResponse[];
  joinCall: (callId: number, options?: StartCallOptions) => Promise<void>;
}

const CallContext = createContext<CallContextValue>({
  incomingCall: null,
  activeCall: null,
  startCall: async () => {},
  acceptIncoming: async () => {},
  rejectIncoming: async () => {},
  endActiveCall: async () => {},
  joinableCalls: [],
  joinCall: async () => {},
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

/** A call that is still ringing for me, in the shape of the realtime INCOMING event. */
function incomingFromResponse(call: CallResponse): CallEvent {
  return {
    type: 'INCOMING',
    callId: call.id,
    conversationId: call.conversationId,
    callerId: call.callerId,
    actorId: call.callerId,
    callType: call.callType,
    status: call.status,
    agoraChannelName: call.agoraChannelName,
    startedAt: call.startedAt,
    answeredAt: call.answeredAt,
    endedAt: call.endedAt,
    endReason: call.endReason,
    participants: call.participants,
  };
}

/** Events after which the set of calls I could join may have changed. */
const ROSTER_EVENTS: ReadonlySet<CallEvent['type']> = new Set([
  'ACCEPTED', 'REJECTED', 'CANCELLED', 'MISSED', 'ENDED',
  'PARTICIPANT_INVITED', 'PARTICIPANT_JOINED', 'PARTICIPANT_LEFT', 'PARTICIPANT_DECLINED', 'CONVERSATION_CHANGED',
]);

function openConversation(id: number): void {
  document.dispatchEvent(new CustomEvent<number>(OPEN_CONVERSATION_EVENT, { detail: id }));
}

interface Props {
  currentUserId: number;
  getUserName: (userId: number) => string;
  getUserAvatar: (userId: number) => string | undefined;
  onError?: (message: string) => void;
  /** Element the call panel is rendered into (the app's main pane). */
  panelHost?: HTMLElement | null;
  children: React.ReactNode;
}

export function CallProvider({ currentUserId, getUserName, getUserAvatar, onError, panelHost, children }: Props) {
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

  // Catching up after being offline. Realtime events reach only connected
  // clients, so someone who opens the app while a call is ringing for them, or
  // while a group call is running, asks the server what they missed.
  const [joinableCalls, setJoinableCalls] = useState<CallResponse[]>([]);
  const refreshTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const refreshActive = useCallback(async () => {
    try {
      const res = await callApi.active();
      setJoinableCalls(res.joinable);
      const ring = res.ringing[0];
      if (ring && !activeCallRef.current && !incomingCallRef.current) {
        setIncomingCall(incomingFromResponse(ring));
      }
    } catch {
      /* offline or signed out: the next trigger tries again */
    }
  }, []);

  const scheduleRefresh = useCallback(() => {
    if (refreshTimer.current) clearTimeout(refreshTimer.current);
    refreshTimer.current = setTimeout(() => void refreshActive(), 300);
  }, [refreshActive]);

  useEffect(() => () => {
    if (refreshTimer.current) clearTimeout(refreshTimer.current);
  }, []);

  // On first connect and after every reconnect.
  const { status: wsStatus } = useRealtime();
  useEffect(() => {
    if (wsStatus === 'connected') void refreshActive();
  }, [wsStatus, refreshActive]);

  // Back to the tab or app (phones suspend background tabs).
  useEffect(() => {
    const onVisible = () => {
      if (document.visibilityState === 'visible') scheduleRefresh();
    };
    document.addEventListener('visibilitychange', onVisible);
    return () => document.removeEventListener('visibilitychange', onVisible);
  }, [scheduleRefresh]);

  // A CALL message in any chat means a call finished there.
  useEffect(() => {
    const onMessage = (e: Event) => {
      if ((e as CustomEvent<{ type?: string }>).detail?.type === 'CALL') scheduleRefresh();
    };
    document.addEventListener('chat:stomp-message', onMessage);
    return () => document.removeEventListener('chat:stomp-message', onMessage);
  }, [scheduleRefresh]);

  const joinCall = useCallback(
    async (callId: number, options?: StartCallOptions) => {
      if (activeCallRef.current) return;
      try {
        const call = await callApi.join(callId);
        setIncomingCall((prev) => (prev?.callId === callId ? null : prev));
        setJoinableCalls((prev) => prev.filter((c) => c.id !== callId));
        setActiveCall(fromResponse(call, false, options));
        try {
          await joinChannel(call.id, call.callType);
        } catch (err) {
          void callApi.end(call.id).catch(() => {});
          throw err;
        }
      } catch (err) {
        handleError(err);
        setActiveCall(null);
        scheduleRefresh();
      }
    },
    [joinChannel, handleError, scheduleRefresh]
  );

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
      if (ROSTER_EVENTS.has(event.type)) scheduleRefresh();

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
        // e.g. my ringing timed out while the group call carries on.
        const stillRingingMe = event.participants.some((p) => p.userId === currentUserId && p.state === 'INVITED');
        if (TERMINAL.has(event.type) || answeredElsewhere || !stillRingingMe) {
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
  }, [currentUserId, leave, onError, scheduleRefresh]);

  const caller = incomingCall?.participants.find((p) => p.userId === incomingCall.callerId);
  const incomingName = caller?.displayName ?? (incomingCall ? getUserName(incomingCall.callerId) : '');
  const incomingAvatar =
    caller?.avatarUrl ?? (incomingCall ? getUserAvatar(incomingCall.callerId) : undefined) ?? undefined;
  const incomingPeople = incomingCall
    ? incomingCall.participants.filter((p) => p.state === 'JOINED' || p.state === 'INVITED').length
    : 0;

  // System notification while the app is in the background (if allowed in Settings).
  useEffect(() => {
    if (!incomingCall || typeof Notification === 'undefined' || Notification.permission !== 'granted') return;
    if (document.visibilityState === 'visible' && document.hasFocus()) return;
    let note: Notification | null = null;
    try {
      note = new Notification(`Incoming ${incomingCall.callType === 'VIDEO' ? 'video' : 'voice'} call`, {
        body: incomingPeople > 2 ? `${incomingName} and ${incomingPeople - 2} others` : incomingName,
        tag: `call-${incomingCall.callId}`,
        icon: incomingAvatar,
        requireInteraction: true,
      });
      note.onclick = () => {
        window.focus();
        note?.close();
      };
    } catch {
      /* Some mobile browsers only allow notifications from a service worker. */
    }
    return () => note?.close();
    // Once per ringing call; later name or avatar updates must not notify again.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [incomingCall?.callId]);

  const callPanel = activeCall && (
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
  );

  return (
    <CallContext.Provider
      value={{
        incomingCall,
        activeCall,
        startCall,
        acceptIncoming,
        rejectIncoming,
        endActiveCall,
        joinableCalls,
        joinCall,
      }}
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

      {callPanel && (panelHost ? createPortal(callPanel, panelHost) : callPanel)}
    </CallContext.Provider>
  );
}
