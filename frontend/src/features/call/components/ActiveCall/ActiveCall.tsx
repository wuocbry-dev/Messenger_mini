import React, { useState, useEffect } from 'react';
import { Avatar } from '../../../../shared/components/ui';
import { useAgora } from '../../../call/hooks';
import './ActiveCall.css';

interface ActiveCallProps {
  callId: number;
  channelName: string;
  peerName: string;
  peerAvatar?: string;
  isInitiator: boolean;
  startedAt: number;
  onEnd: () => void;
}

function formatDuration(ms: number): string {
  const totalSec = Math.floor(ms / 1000);
  const min = Math.floor(totalSec / 60);
  const sec = totalSec % 60;
  return `${min}:${sec.toString().padStart(2, '0')}`;
}

export function ActiveCall({ peerName, peerAvatar, isInitiator, startedAt, onEnd }: ActiveCallProps) {
  const { state, toggleMute, leave } = useAgora();
  const [elapsed, setElapsed] = useState(0);

  // Update duration every second when connected
  useEffect(() => {
    if (state.phase !== 'joined' || !state.remoteJoined) return;
    const interval = setInterval(() => {
      setElapsed(Date.now() - startedAt);
    }, 1000);
    return () => clearInterval(interval);
  }, [state.phase, state.remoteJoined, startedAt]);

  const handleEnd = async () => {
    await leave();
    onEnd();
  };

  const phaseLabel =
    state.phase === 'idle' ? 'Connecting...' :
    state.phase === 'requesting' ? 'Requesting microphone...' :
    state.phase === 'joining' ? 'Joining channel...' :
    state.phase === 'joined' && state.remoteJoined ? formatDuration(elapsed) :
    state.phase === 'joined' ? 'Waiting for peer...' :
    state.phase === 'error' ? `Error: ${state.error}` :
    'Connecting...';

  return (
    <div className="active-call" role="dialog" aria-label="Voice call">
      <div className="active-call-card animate-fade-in">
        <div className="active-call-peer">
          <div className={`active-call-avatar${state.remoteJoined ? ' active-call-avatar--connected' : ''}`}>
            <Avatar name={peerName} src={peerAvatar} size="lg" />
            {state.remoteJoined && (
              <span className="active-call-connected-dot" aria-hidden="true" />
            )}
          </div>
          <h2 className="active-call-name">{peerName}</h2>
          <span className="active-call-status" aria-live="polite">
            {phaseLabel}
          </span>
        </div>

        <div className="active-call-controls">
          <button
            className={`active-call-btn${state.localMuted ? ' active-call-btn--muted' : ''}`}
            onClick={toggleMute}
            aria-label={state.localMuted ? 'Unmute microphone' : 'Mute microphone'}
            aria-pressed={state.localMuted}
          >
            {state.localMuted ? (
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <line x1="1" y1="1" x2="23" y2="23" />
                <path d="M9 9v3a3 3 0 0 0 5.12 2.12M15 9.34V4a3 3 0 0 0-5.94-.6" />
                <path d="M17 16.95A7 7 0 0 1 5 12v-2m14 0v2a7 7 0 0 1-.11 1.23" />
                <line x1="12" y1="19" x2="12" y2="23" />
                <line x1="8" y1="23" x2="16" y2="23" />
              </svg>
            ) : (
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z" />
                <path d="M19 10v2a7 7 0 0 1-14 0v-2" />
                <line x1="12" y1="19" x2="12" y2="23" />
                <line x1="8" y1="23" x2="16" y2="23" />
              </svg>
            )}
            <span>{state.localMuted ? 'Unmute' : 'Mute'}</span>
          </button>

          <button
            className="active-call-btn active-call-btn--end"
            onClick={handleEnd}
            aria-label="End call"
          >
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07A19.5 19.5 0 0 1 4.69 12 19.79 19.79 0 0 1 1.61 3.38 2 2 0 0 1 3.6 1.22h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z" transform="rotate(135 12 12)" />
            </svg>
            <span>End</span>
          </button>
        </div>
      </div>
    </div>
  );
}
