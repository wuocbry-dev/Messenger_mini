import type { MessageCall } from '../../../shared/types';

export interface CallMessageText {
  /** e.g. "Video call", "Missed voice call", "Group voice call". */
  title: string;
  /** e.g. "3 min 12 sec", "No answer", "Declined"; empty when nothing to add. */
  detail: string;
  /** Shown in red: a call this viewer did not pick up. */
  missed: boolean;
  video: boolean;
}

/** "45 sec", "3 min 12 sec", "1 hr 5 min". */
export function formatCallDuration(seconds: number): string {
  const s = Math.max(0, Math.round(seconds));
  if (s < 60) return `${s} sec`;
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const rest = s % 60;
  if (h > 0) return m > 0 ? `${h} hr ${m} min` : `${h} hr`;
  return rest > 0 ? `${m} min ${rest} sec` : `${m} min`;
}

/** Words a finished call from the point of view of the person reading the chat. */
export function describeCallMessage(call: MessageCall, currentUserId: number): CallMessageText {
  const video = call.callType === 'VIDEO';
  const kind = video ? 'video' : 'voice';
  const iCalled = call.callerId === currentUserId;
  /** "Voice call", "Video call", "Group voice call". */
  const name = call.groupCall ? `Group ${kind} call` : `${video ? 'Video' : 'Voice'} call`;

  switch (call.status) {
    case 'ENDED': {
      const parts = [];
      if (call.durationSeconds != null) parts.push(formatCallDuration(call.durationSeconds));
      if (call.groupCall && call.joinedCount > 2) parts.push(`${call.joinedCount} people`);
      return { title: name, detail: parts.join(' · '), missed: false, video };
    }
    case 'MISSED':
    case 'CANCELLED':
      return iCalled
        ? { title: name, detail: call.status === 'MISSED' ? 'No answer' : 'Cancelled', missed: false, video }
        : { title: `Missed ${call.groupCall ? 'group ' : ''}${kind} call`, detail: '', missed: true, video };
    case 'REJECTED':
      return { title: name, detail: 'Declined', missed: false, video };
    default:
      return { title: name, detail: 'Call failed', missed: false, video };
  }
}

/** One-line text for the conversation list. */
export function callPreview(call: MessageCall, currentUserId: number): string {
  const { title, detail } = describeCallMessage(call, currentUserId);
  return detail ? `${title} · ${detail}` : title;
}
