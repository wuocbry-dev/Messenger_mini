import { useEffect, useRef } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useCall } from '../features/call/components/CallProvider';

export interface StartCallRequest {
  conversationId: number;
  /** null rings everyone in a group conversation. */
  calleeId: number | null;
  group?: boolean;
  callType: 'VOICE' | 'VIDEO';
  peerName?: string;
  peerAvatar?: string;
  /** Unique per request so a re-render never starts the call twice. */
  nonce: number;
}

/**
 * Starts a call requested by another page (e.g. "Call again" in call history)
 * through router state. Must render inside CallProvider.
 */
export function PendingCallLauncher() {
  const location = useLocation();
  const navigate = useNavigate();
  const { startCall } = useCall();
  const handled = useRef<number | null>(null);

  useEffect(() => {
    const req = (location.state as { startCall?: StartCallRequest } | null)?.startCall;
    if (!req || handled.current === req.nonce) return;
    handled.current = req.nonce;
    // Also open that conversation behind the call UI.
    navigate(location.pathname, { replace: true, state: { openConversationId: req.conversationId } });
    void startCall(req.conversationId, req.calleeId, req.callType, { group: req.group });
  }, [location, navigate, startCall]);

  return null;
}
