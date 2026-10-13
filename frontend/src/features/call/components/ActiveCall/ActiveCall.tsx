import React, { useState, useEffect, useMemo, useRef } from 'react';
import type { ILocalVideoTrack, IRemoteVideoTrack } from 'agora-rtc-sdk-ng';
import { Avatar } from '../../../../shared/components/ui';
import type { CallParticipant } from '../../../../shared/types';
import type { AgoraState } from '../../hooks/useAgora';
import { AddPeopleModal } from '../AddPeopleModal';
import './ActiveCall.css';

interface ActiveCallProps {
  /** Group chat name, if any; otherwise the people in the call are named. */
  title?: string;
  participants: CallParticipant[];
  currentUserId: number;
  video: boolean;
  /** Epoch ms when someone else's audio connected; null while ringing/connecting. */
  connectedAt: number | null;
  ringing: boolean;
  state: AgoraState;
  /** A 1-to-1 chat turns into a new group chat when someone is added. */
  inviteCreatesGroup: boolean;
  onToggleMute: () => void;
  onToggleCamera: () => void;
  onToggleSpeaker: () => void;
  onToggleShare: () => void;
  onUpgradeToVideo: () => void;
  onInvite: (userIds: number[]) => Promise<void>;
  listCameras: () => Promise<{ id: string; label: string }[]>;
  onSwitchCamera: (deviceId: string) => void;
  onEnd: () => void;
}

const WAVE_BARS = 27;
const MAX_PARTICIPANTS = 8;

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
  track: ILocalVideoTrack | IRemoteVideoTrack | null;
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

const CameraIcon = ({ off, size = 22 }: { off: boolean; size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <polygon points="23 7 16 12 23 17 23 7" />
    <rect x="1" y="5" width="15" height="14" rx="2" ry="2" />
    {off && <line x1="1" y1="1" x2="23" y2="23" />}
  </svg>
);

const AddPersonIcon = ({ size = 22 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
    <circle cx="9" cy="7" r="4" />
    <line x1="19" y1="8" x2="19" y2="14" />
    <line x1="16" y1="11" x2="22" y2="11" />
  </svg>
);

const FullscreenIcon = ({ exit }: { exit: boolean }) => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {exit ? (
      <path d="M8 3v3a2 2 0 0 1-2 2H3m18 0h-3a2 2 0 0 1-2-2V3m0 18v-3a2 2 0 0 1 2-2h3M3 16h3a2 2 0 0 1 2 2v3" />
    ) : (
      <path d="M8 3H5a2 2 0 0 0-2 2v3m18 0V5a2 2 0 0 0-2-2h-3m0 18h3a2 2 0 0 0 2-2v-3M3 16v3a2 2 0 0 0 2 2h3" />
    )}
  </svg>
);

const EndIcon = () => (
  <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07A19.5 19.5 0 0 1 4.69 12 19.79 19.79 0 0 1 1.61 3.38 2 2 0 0 1 3.6 1.22h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z" transform="rotate(135 12 12)" />
  </svg>
);

export function ActiveCall({
  title,
  participants,
  currentUserId,
  video,
  connectedAt,
  ringing,
  state,
  inviteCreatesGroup,
  onToggleMute,
  onToggleCamera,
  onToggleSpeaker,
  onToggleShare,
  onUpgradeToVideo,
  onInvite,
  listCameras,
  onSwitchCamera,
  onEnd,
}: ActiveCallProps) {
  const [elapsed, setElapsed] = useState(0);
  const [minimized, setMinimized] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [addOpen, setAddOpen] = useState(false);
  const [cameras, setCameras] = useState<{ id: string; label: string }[]>([]);
  const [fullscreen, setFullscreen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);

  const present = useMemo(
    () => participants.filter((p) => p.state === 'JOINED' || p.state === 'INVITED'),
    [participants]
  );
  const others = present.filter((p) => p.userId !== currentUserId);
  const isGroup = present.length > 2;
  const peer = others[0] ?? participants.find((p) => p.userId !== currentUserId);
  const peerName = isGroup ? title || others.map((p) => p.displayName).join(', ') : peer?.displayName ?? title ?? 'Call';
  const peerAvatar = peer?.avatarUrl ?? undefined;
  const headline = isGroup ? `${peerName}` : peerName;
  const room = Math.max(0, MAX_PARTICIPANTS - present.length);

  useEffect(() => {
    const onChange = () => setFullscreen(document.fullscreenElement === rootRef.current);
    document.addEventListener('fullscreenchange', onChange);
    return () => {
      document.removeEventListener('fullscreenchange', onChange);
      if (document.fullscreenElement) void document.exitFullscreen().catch(() => {});
    };
  }, []);

  const toggleFullscreen = () => {
    if (document.fullscreenElement) void document.exitFullscreen().catch(() => {});
    else void rootRef.current?.requestFullscreen().catch(() => {});
  };

  useEffect(() => {
    if (settingsOpen) void listCameras().then(setCameras);
  }, [settingsOpen, listCameras]);

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
  const peopleLabel = `${present.filter((p) => p.state === 'JOINED').length} in call`;

  const addModal = (
    <AddPeopleModal
      open={addOpen}
      onClose={() => setAddOpen(false)}
      excludeIds={present.map((p) => p.userId)}
      room={room}
      onInvite={onInvite}
      createsGroup={inviteCreatesGroup}
    />
  );

  /* ── Minimized pill (voice and video) ─────────────────────── */
  if (minimized) {
    return (
      <>
        <div className="active-call active-call--mini" role="dialog" aria-label="Call (minimized)">
          <button className="call-mini-main" onClick={() => setMinimized(false)} aria-label="Expand call">
            <Avatar name={peerName} src={peerAvatar} size="sm" />
            <span className="call-mini-text">
              <span className="call-mini-name">{headline}</span>
              <span className="call-mini-status">{statusLabel}</span>
            </span>
          </button>
          <button className="call-mini-end" onClick={onEnd} aria-label="End call">
            <EndIcon />
          </button>
        </div>
        {addModal}
      </>
    );
  }

  /* ── Video call: stage layout ─────────────────────────────── */
  if (video) {
    const sharing = state.sharing;
    const tiles = others.length > 0 ? others : peer ? [peer] : [];
    return (
      <div
        ref={rootRef}
        className={`active-call active-call--video${connected ? ' active-call--video-full' : ''}`}
        role="dialog"
        aria-label="Video call"
      >
        <div className="vcall animate-fade-in">
          <div className="vcall-stage">
            <div className={`vcall-grid vcall-grid--${Math.min(Math.max(tiles.length, 1), 6)}`}>
              {tiles.map((p) => {
                const track = state.remoteVideos[p.userId];
                const ringingTile = p.state === 'INVITED';
                return (
                  <div key={p.userId} className={`vcall-tile${ringingTile ? ' vcall-tile--ringing' : ''}`}>
                    {track ? (
                      <VideoView track={track} className="vcall-remote" />
                    ) : (
                      <div className="vcall-placeholder">
                        <Avatar name={p.displayName} src={p.avatarUrl ?? undefined} size="xl" />
                        {!ringingTile && connected && <span className="vcall-placeholder-note">Camera is off</span>}
                      </div>
                    )}
                    <span className="vcall-tile-name">
                      {p.displayName}
                      {ringingTile ? ' · Ringing...' : ''}
                    </span>
                  </div>
                );
              })}
            </div>

            <header className="vcall-top">
              <div className="vcall-peer">
                <Avatar name={peerName} src={peerAvatar} size="md" />
                <div className="vcall-peer-text">
                  <span className="vcall-peer-name">{headline}</span>
                  <span className={`vcall-peer-status${connected ? ' vcall-peer-status--on' : ''}`}>
                    <span className="vcall-dot" aria-hidden="true" />
                    {isGroup ? peopleLabel : connected ? 'Online' : ringing ? 'Calling' : 'Connecting'}
                  </span>
                </div>
                <span className="vcall-timer" aria-live="polite">{statusLabel}</span>
              </div>
              <div className="vcall-top-actions">
                <button className="vcall-icon-btn" onClick={() => setMinimized(true)} aria-label="Minimize call" title="Minimize">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round"><line x1="5" y1="12" x2="19" y2="12" /></svg>
                </button>
                <button className="vcall-icon-btn" onClick={toggleFullscreen} aria-label={fullscreen ? 'Exit full screen' : 'Full screen'} title="Full screen">
                  <FullscreenIcon exit={fullscreen} />
                </button>
              </div>
            </header>

            {state.localVideo && (state.cameraOn || sharing) && (
              <VideoView track={state.localVideo} className={`vcall-local${sharing ? ' vcall-local--screen' : ''}`} />
            )}

            {state.notice && <p className="vcall-notice" role="status">{state.notice}</p>}

            {settingsOpen && (
              <div className="vcall-settings" role="menu">
                <div className="vcall-settings-row">
                  <span>Speaker</span>
                  <button
                    className={`vcall-switch${state.speakerOn ? ' vcall-switch--on' : ''}`}
                    onClick={onToggleSpeaker}
                    role="switch"
                    aria-checked={state.speakerOn}
                    aria-label="Speaker"
                  >
                    <span />
                  </button>
                </div>
                {cameras.length > 1 && (
                  <label className="vcall-settings-row vcall-settings-row--col">
                    <span>Camera</span>
                    <select onChange={(e) => onSwitchCamera(e.target.value)} defaultValue="">
                      <option value="" disabled>Choose camera...</option>
                      {cameras.map((c) => (
                        <option key={c.id} value={c.id}>{c.label}</option>
                      ))}
                    </select>
                  </label>
                )}
              </div>
            )}

            <div className="vcall-bar">
              <button
                className={`vcall-ctl${state.localMuted ? ' vcall-ctl--off' : ''}`}
                onClick={onToggleMute}
                aria-pressed={state.localMuted}
                aria-label={state.localMuted ? 'Unmute microphone' : 'Mute microphone'}
              >
                <span className="vcall-ctl-circle"><MicIcon off={state.localMuted} /></span>
                <span className="vcall-ctl-label">{state.localMuted ? 'Unmute' : 'Mute'}</span>
              </button>
              <button
                className={`vcall-ctl${!state.cameraOn ? ' vcall-ctl--off' : ''}`}
                onClick={onToggleCamera}
                disabled={sharing}
                aria-pressed={!state.cameraOn}
                aria-label={state.cameraOn ? 'Stop video' : 'Start video'}
              >
                <span className="vcall-ctl-circle"><CameraIcon off={!state.cameraOn} /></span>
                <span className="vcall-ctl-label">{state.cameraOn ? 'Stop Video' : 'Start Video'}</span>
              </button>
              <button
                className={`vcall-ctl${sharing ? ' vcall-ctl--active' : ''}`}
                onClick={onToggleShare}
                disabled={!connected}
                aria-pressed={sharing}
                aria-label={sharing ? 'Stop sharing' : 'Share screen'}
              >
                <span className="vcall-ctl-circle">
                  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <rect x="3" y="4" width="18" height="14" rx="2" />
                    <polyline points="9 10 12 7 15 10" />
                    <line x1="12" y1="7" x2="12" y2="14" />
                    <line x1="8" y1="21" x2="16" y2="21" />
                  </svg>
                </span>
                <span className="vcall-ctl-label">{sharing ? 'Stop Share' : 'Share'}</span>
              </button>
              <button className="vcall-ctl" onClick={() => setAddOpen(true)} disabled={room <= 0} aria-label="Add people to the call">
                <span className="vcall-ctl-circle"><AddPersonIcon /></span>
                <span className="vcall-ctl-label">Add</span>
              </button>
              <button className="vcall-ctl" onClick={() => setMinimized(true)} aria-label="Back to chat">
                <span className="vcall-ctl-circle">
                  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
                  </svg>
                </span>
                <span className="vcall-ctl-label">Chat</span>
              </button>
              <button
                className={`vcall-ctl${settingsOpen ? ' vcall-ctl--active' : ''}`}
                onClick={() => setSettingsOpen((o) => !o)}
                aria-expanded={settingsOpen}
                aria-label="Call settings"
              >
                <span className="vcall-ctl-circle">
                  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <circle cx="12" cy="12" r="3" />
                    <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09a1.65 1.65 0 0 0-1-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09a1.65 1.65 0 0 0 1.51-1 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33h0a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51h0a1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82v0a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
                  </svg>
                </span>
                <span className="vcall-ctl-label">Settings</span>
              </button>
              <button className="vcall-ctl" onClick={toggleFullscreen} aria-label={fullscreen ? 'Exit full screen' : 'Full screen'}>
                <span className="vcall-ctl-circle"><FullscreenIcon exit={fullscreen} /></span>
                <span className="vcall-ctl-label">Fullscreen</span>
              </button>
              <span className="vcall-sep" aria-hidden="true" />
              <button className="vcall-ctl vcall-ctl--end" onClick={onEnd} aria-label="End call">
                <span className="vcall-ctl-circle"><EndIcon /></span>
                <span className="vcall-ctl-label">End Call</span>
              </button>
            </div>
          </div>
        </div>
        {addModal}
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
              <span className="call-panel-peer-name">{headline}</span>
              <span className={`call-panel-peer-status${connected ? ' call-panel-peer-status--on' : ''}`}>
                <span className="call-panel-dot" aria-hidden="true" />
                {isGroup ? peopleLabel : connected ? 'In call' : ringing ? 'Calling' : 'Connecting'}
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
          {isGroup ? (
            <div className={`call-grid call-grid--${Math.min(present.length, 8)}`}>
              {present.map((p) => {
                const me = p.userId === currentUserId;
                const joined = p.state === 'JOINED';
                return (
                  <div key={p.userId} className={`call-tile${joined ? ' call-tile--live' : ''}`}>
                    <div className="call-tile-avatar">
                      <Avatar name={p.displayName} src={p.avatarUrl ?? undefined} size="xl" />
                    </div>
                    <span className="call-tile-name">{me ? 'You' : p.displayName}</span>
                    <span className="call-tile-sub">{joined ? 'Connected' : 'Ringing...'}</span>
                  </div>
                );
              })}
            </div>
          ) : (
            <>
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
            </>
          )}

          <div className="call-timer" aria-live="polite">{statusLabel}</div>
          <span className="call-quality">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" aria-hidden="true">
              <line x1="4" y1="10" x2="4" y2="14" />
              <line x1="8" y1="6" x2="8" y2="18" />
              <line x1="12" y1="3" x2="12" y2="21" />
              <line x1="16" y1="7" x2="16" y2="17" />
              <line x1="20" y1="10" x2="20" y2="14" />
            </svg>
            {isGroup ? 'Group voice call' : 'HD Voice Call'}
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

          <button className="call-ctl" onClick={onUpgradeToVideo} aria-label="Switch to video call">
            <span className="call-ctl-circle"><CameraIcon off={false} size={26} /></span>
            <span className="call-ctl-label">Video</span>
            <span className="call-ctl-sub">Switch</span>
          </button>

          <button className="call-ctl" onClick={() => setAddOpen(true)} disabled={room <= 0} aria-label="Add people to the call">
            <span className="call-ctl-circle"><AddPersonIcon size={26} /></span>
            <span className="call-ctl-label">Add</span>
            <span className="call-ctl-sub">{isGroup ? `${present.length} people` : 'Group call'}</span>
          </button>

          <button className="call-ctl call-ctl--end" onClick={onEnd} aria-label="End call">
            <span className="call-ctl-circle"><EndIcon /></span>
            <span className="call-ctl-label">End Call</span>
          </button>
        </div>
      </div>
      {addModal}
    </div>
  );
}
