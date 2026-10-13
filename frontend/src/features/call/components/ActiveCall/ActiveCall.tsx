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
  onToggleSpeaker: () => void;
  onEnd: () => void;
}

const WAVE_BARS = 27;

function formatDuration(ms: number): string {
  const totalSec = Math.floor(ms / 1000);
  const min = Math.floor(totalSec / 60);
  const sec = totalSec % 60;
  return `${min.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}`;
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

const MicIcon = ({ off }: { off: boolean }) => (
  <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z" />
    <path d="M19 10v2a7 7 0 0 1-14 0v-2" />
    <line x1="12" y1="19" x2="12" y2="23" />
    <line x1="8" y1="23" x2="16" y2="23" />
    {off && <line x1="2" y1="2" x2="22" y2="22" />}
  </svg>
);

const SpeakerIcon = ({ off }: { off: boolean }) => (
  <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5" />
    {off ? (
      <>
        <line x1="23" y1="9" x2="17" y2="15" />
        <line x1="17" y1="9" x2="23" y2="15" />
      </>
    ) : (
      <>
        <path d="M15.54 8.46a5 5 0 0 1 0 7.07" />
        <path d="M19.07 4.93a10 10 0 0 1 0 14.14" />
      </>
    )}
  </svg>
);

const EndIcon = () => (
  <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07A19.5 19.5 0 0 1 4.69 12 19.79 19.79 0 0 1 1.61 3.38 2 2 0 0 1 3.6 1.22h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z" transform="rotate(135 12 12)" />
  </svg>
);

export function ActiveCall({
  peerName,
  peerAvatar,
  video,
  connectedAt,
  ringing,
  state,
  onToggleMute,
  onToggleCamera,
  onToggleSpeaker,
  onEnd,
}: ActiveCallProps) {
  const [elapsed, setElapsed] = useState(0);
  const [minimized, setMinimized] = useState(false);

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

  const statusLabel =
    state.phase === 'error' ? `Error: ${state.error}` :
    state.phase === 'requesting' ? (video ? 'Requesting camera & microphone...' : 'Requesting microphone...') :
    state.phase === 'joining' ? 'Joining channel...' :
    state.remoteJoined ? formatDuration(elapsed) :
    ringing ? 'Ringing...' :
    'Connecting...';

  const connected = state.remoteJoined;

  /* ── Video call: stage layout ─────────────────────────────── */
  if (video) {
    return (
      <div className="active-call active-call--video" role="dialog" aria-label="Video call">
        <div className="active-call-card animate-fade-in">
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
              <span className="active-call-status" aria-live="polite">{statusLabel}</span>
            </div>
          </div>

          {state.notice && <p className="active-call-notice" role="status">{state.notice}</p>}

          <div className="call-controls call-controls--inline">
            <button
              className={`call-ctl${state.localMuted ? ' call-ctl--off' : ''}`}
              onClick={onToggleMute}
              aria-label={state.localMuted ? 'Unmute microphone' : 'Mute microphone'}
              aria-pressed={state.localMuted}
            >
              <span className="call-ctl-circle"><MicIcon off={state.localMuted} /></span>
              <span className="call-ctl-label">{state.localMuted ? 'Unmute' : 'Mute'}</span>
            </button>
            {state.localVideo !== null && (
              <button
                className={`call-ctl${!state.cameraOn ? ' call-ctl--off' : ''}`}
                onClick={onToggleCamera}
                aria-label={state.cameraOn ? 'Turn camera off' : 'Turn camera on'}
                aria-pressed={!state.cameraOn}
              >
                <span className="call-ctl-circle">
                  <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <polygon points="23 7 16 12 23 17 23 7" />
                    <rect x="1" y="5" width="15" height="14" rx="2" ry="2" />
                    {!state.cameraOn && <line x1="1" y1="1" x2="23" y2="23" />}
                  </svg>
                </span>
                <span className="call-ctl-label">{state.cameraOn ? 'Camera' : 'Camera off'}</span>
              </button>
            )}
            <button className="call-ctl call-ctl--end" onClick={onEnd} aria-label="End call">
              <span className="call-ctl-circle"><EndIcon /></span>
              <span className="call-ctl-label">End Call</span>
            </button>
          </div>
        </div>
      </div>
    );
  }

  /* ── Voice call: minimized pill ───────────────────────────── */
  if (minimized) {
    return (
      <div className="active-call active-call--mini" role="dialog" aria-label="Voice call (minimized)">
        <button className="call-mini-main" onClick={() => setMinimized(false)} aria-label="Expand call">
          <Avatar name={peerName} src={peerAvatar} size="sm" />
          <span className="call-mini-text">
            <span className="call-mini-name">{peerName}</span>
            <span className="call-mini-status">{statusLabel}</span>
          </span>
        </button>
        <button className="call-mini-end" onClick={onEnd} aria-label="End call">
          <EndIcon />
        </button>
      </div>
    );
  }

  /* ── Voice call: full panel ───────────────────────────────── */
  return (
    <div className="active-call active-call--panel" role="dialog" aria-label="Voice call">
      <div className="call-panel animate-fade-in">
        <header className="call-panel-header">
          <div className="call-panel-peer">
            <Avatar name={peerName} src={peerAvatar} size="md" />
            <div className="call-panel-peer-text">
              <span className="call-panel-peer-name">{peerName}</span>
              <span className={`call-panel-peer-status${connected ? ' call-panel-peer-status--on' : ''}`}>
                <span className="call-panel-dot" aria-hidden="true" />
                {connected ? 'In call' : ringing ? 'Calling' : 'Connecting'}
              </span>
            </div>
          </div>
          <button className="call-minimize" onClick={() => setMinimized(true)}>
            <span className="call-minimize-icon" aria-hidden="true">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
              </svg>
            </span>
            Minimize to chat
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <line x1="7" y1="17" x2="17" y2="7" />
              <polyline points="7 7 17 7 17 17" />
            </svg>
          </button>
        </header>

        <div className="call-panel-body">
          <div className={`call-avatar${connected ? ' call-avatar--live' : ''}`}>
            <span className="call-avatar-ring call-avatar-ring--outer" aria-hidden="true" />
            <span className="call-avatar-ring call-avatar-ring--mid" aria-hidden="true" />
            <div className="call-avatar-photo">
              <Avatar name={peerName} src={peerAvatar} size="xl" />
            </div>
          </div>

          <div className={`call-wave${connected ? ' call-wave--live' : ''}`} aria-hidden="true">
            {Array.from({ length: WAVE_BARS }).map((_, i) => (
              <span key={i} style={{ ['--i' as string]: i }} />
            ))}
          </div>

          <h2 className="call-name">{peerName}</h2>
          <div className="call-timer" aria-live="polite">{statusLabel}</div>
          <span className="call-quality">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" aria-hidden="true">
              <line x1="4" y1="10" x2="4" y2="14" />
              <line x1="8" y1="6" x2="8" y2="18" />
              <line x1="12" y1="3" x2="12" y2="21" />
              <line x1="16" y1="7" x2="16" y2="17" />
              <line x1="20" y1="10" x2="20" y2="14" />
            </svg>
            HD Voice Call
          </span>
          {state.notice && <p className="active-call-notice" role="status">{state.notice}</p>}
        </div>

        <div className="call-controls">
          <button
            className={`call-ctl${state.localMuted ? ' call-ctl--off' : ''}`}
            onClick={onToggleMute}
            aria-label={state.localMuted ? 'Unmute microphone' : 'Mute microphone'}
            aria-pressed={state.localMuted}
          >
            <span className="call-ctl-circle"><MicIcon off={state.localMuted} /></span>
            <span className="call-ctl-label">Mute</span>
            <span className="call-ctl-sub">{state.localMuted ? 'On' : 'Off'}</span>
          </button>

          <button
            className={`call-ctl${state.speakerOn ? ' call-ctl--active' : ''}`}
            onClick={onToggleSpeaker}
            aria-label={state.speakerOn ? 'Turn speaker off' : 'Turn speaker on'}
            aria-pressed={state.speakerOn}
          >
            <span className="call-ctl-circle"><SpeakerIcon off={!state.speakerOn} /></span>
            <span className="call-ctl-label">Speaker</span>
            <span className="call-ctl-sub call-ctl-sub--accent">{state.speakerOn ? 'On' : 'Off'}</span>
          </button>

          <button className="call-ctl call-ctl--end" onClick={onEnd} aria-label="End call">
            <span className="call-ctl-circle"><EndIcon /></span>
            <span className="call-ctl-label">End Call</span>
          </button>
        </div>
      </div>
    </div>
  );
}
