import React, { useState, useEffect, useRef } from 'react';
import type { ICameraVideoTrack, IRemoteVideoTrack } from 'agora-rtc-sdk-ng';
import { Avatar } from '../../../../shared/components/ui';
import type { AgoraState } from '../../hooks/useAgora';
import './ActiveCall.css';

interface ActiveCallProps {
  peerName: string;
  peerAvatar?: string;
  video: boolean;
  /** Epoch ms when the peer's audio connected; null while ringing/connecting. */
  connectedAt: number | null;
  ringing: boolean;
  state: AgoraState;
  onToggleMute: () => void;
  onToggleCamera: () => void;
  onEnd: () => void;
}

function formatDuration(ms: number): string {
  const totalSec = Math.floor(ms / 1000);
  const min = Math.floor(totalSec / 60);
  const sec = totalSec % 60;
  return `${min}:${sec.toString().padStart(2, '0')}`;
}

/** Renders an Agora video track into a div and stops it on unmount. */
function VideoView({
  track,
  className,
}: {
  track: ICameraVideoTrack | IRemoteVideoTrack | null;
  className: string;
}) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const el = ref.current;
    if (!track || !el) return;
    track.play(el);
    return () => track.stop();
  }, [track]);
  return <div ref={ref} className={className} />;
}

export function ActiveCall({
  peerName,
  peerAvatar,
  video,
  connectedAt,
  ringing,
  state,
  onToggleMute,
  onToggleCamera,
  onEnd,
}: ActiveCallProps) {
  const [elapsed, setElapsed] = useState(0);

  useEffect(() => {
    if (!state.remoteJoined || connectedAt === null) {
      setElapsed(0);
      return;
    }
    const tick = () => setElapsed(Date.now() - connectedAt);
    tick();
    const interval = setInterval(tick, 1000);
    return () => clearInterval(interval);
  }, [state.remoteJoined, connectedAt]);

  const phaseLabel =
    state.phase === 'error' ? `Error: ${state.error}` :
    state.phase === 'requesting' ? (video ? 'Requesting camera & microphone...' : 'Requesting microphone...') :
    state.phase === 'joining' ? 'Joining channel...' :
    state.remoteJoined ? formatDuration(elapsed) :
    ringing ? 'Ringing...' :
    'Connecting...';

  const showVideoStage = video;

  return (
    <div className={`active-call${showVideoStage ? ' active-call--video' : ''}`} role="dialog" aria-label={video ? 'Video call' : 'Voice call'}>
      <div className="active-call-card animate-fade-in">
        {showVideoStage ? (
          <div className="active-call-stage">
            {state.remoteVideo ? (
              <VideoView track={state.remoteVideo} className="active-call-remote-video" />
            ) : (
              <div className="active-call-stage-placeholder">
                <Avatar name={peerName} src={peerAvatar} size="lg" />
              </div>
            )}
            {state.cameraOn && state.localVideo && (
              <VideoView track={state.localVideo} className="active-call-local-video" />
            )}
            <div className="active-call-stage-info">
              <h2 className="active-call-name">{peerName}</h2>
              <span className="active-call-status" aria-live="polite">{phaseLabel}</span>
            </div>
          </div>
        ) : (
          <div className="active-call-peer">
            <div className={`active-call-avatar${state.remoteJoined ? ' active-call-avatar--connected' : ''}`}>
              <Avatar name={peerName} src={peerAvatar} size="lg" />
              {state.remoteJoined && <span className="active-call-connected-dot" aria-hidden="true" />}
            </div>
            <h2 className="active-call-name">{peerName}</h2>
            <span className="active-call-status" aria-live="polite">{phaseLabel}</span>
          </div>
        )}

        {state.notice && <p className="active-call-notice" role="status">{state.notice}</p>}

        <div className="active-call-controls">
          <button
            className={`active-call-btn${state.localMuted ? ' active-call-btn--muted' : ''}`}
            onClick={onToggleMute}
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

          {state.localVideo !== null && (
            <button
              className={`active-call-btn${!state.cameraOn ? ' active-call-btn--muted' : ''}`}
              onClick={onToggleCamera}
              aria-label={state.cameraOn ? 'Turn camera off' : 'Turn camera on'}
              aria-pressed={!state.cameraOn}
            >
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <polygon points="23 7 16 12 23 17 23 7" />
                <rect x="1" y="5" width="15" height="14" rx="2" ry="2" />
                {!state.cameraOn && <line x1="1" y1="1" x2="23" y2="23" />}
              </svg>
              <span>{state.cameraOn ? 'Camera' : 'Camera off'}</span>
            </button>
          )}

          <button
            className="active-call-btn active-call-btn--end"
            onClick={onEnd}
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
