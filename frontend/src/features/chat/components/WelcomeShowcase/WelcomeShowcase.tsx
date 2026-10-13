import React, { useEffect, useState } from 'react';
import './WelcomeShowcase.css';

interface WelcomeShowcaseProps {
  onStartChat: () => void;
}

const SCENE_MS = 4800;

const SCENES = [
  { key: 'chat', label: 'Real-time chat', caption: 'Message anyone instantly, with live typing and read receipts.' },
  { key: 'share', label: 'Photos and files', caption: 'Send several photos at once and browse them in one tap.' },
  { key: 'call', label: 'Voice and video calls', caption: 'Crystal-clear one-to-one calls, right from the chat.' },
  { key: 'presence', label: 'Always in the loop', caption: 'See who is online and when they were last around.' },
] as const;

function prefersReducedMotion(): boolean {
  return typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches === true;
}

function ChatScene() {
  return (
    <div className="ws-scene ws-chat">
      <div className="ws-bubble ws-bubble--in" style={{ ['--d' as string]: '0.2s' }}>Hey! Are you free tonight?</div>
      <div className="ws-bubble ws-bubble--out" style={{ ['--d' as string]: '1.2s' }}>Sure, let&apos;s call at 8</div>
      <div className="ws-bubble ws-bubble--in ws-typing" style={{ ['--d' as string]: '2.3s' }} aria-hidden="true">
        <span /><span /><span />
      </div>
    </div>
  );
}

function ShareScene() {
  return (
    <div className="ws-scene ws-share">
      <span className="ws-share-label">You sent 3 photos</span>
      <div className="ws-stack">
        {[2, 1, 0].map((layer) => (
          <span key={layer} className={`ws-photo ws-photo--${layer}`} style={{ ['--d' as string]: `${(2 - layer) * 0.35}s` }}>
            <svg viewBox="0 0 48 36" aria-hidden="true">
              <circle cx="14" cy="11" r="5" />
              <path d="M2 34 17 17l9 9 7-8 13 16z" />
            </svg>
          </span>
        ))}
      </div>
      <div className="ws-file" style={{ ['--d' as string]: '1.5s' }}>
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
          <polyline points="14 2 14 8 20 8" />
        </svg>
        <span>project-report.pdf</span>
      </div>
    </div>
  );
}

function CallScene() {
  return (
    <div className="ws-scene ws-call">
      <div className="ws-call-avatar">
        <span className="ws-ring ws-ring--1" />
        <span className="ws-ring ws-ring--2" />
        <span className="ws-ring ws-ring--3" />
        <span className="ws-avatar ws-avatar--lg">A</span>
      </div>
      <div className="ws-wave" aria-hidden="true">
        {Array.from({ length: 17 }).map((_, i) => (
          <span key={i} style={{ ['--i' as string]: i }} />
        ))}
      </div>
      <div className="ws-call-controls" aria-hidden="true">
        <span className="ws-round">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z" /><path d="M19 10v2a7 7 0 0 1-14 0v-2" /></svg>
        </span>
        <span className="ws-round ws-round--video">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polygon points="23 7 16 12 23 17 23 7" /><rect x="1" y="5" width="15" height="14" rx="2" /></svg>
        </span>
        <span className="ws-round ws-round--end">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07A19.5 19.5 0 0 1 4.69 12 19.79 19.79 0 0 1 1.61 3.38 2 2 0 0 1 3.6 1.22h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z" transform="rotate(135 12 12)" /></svg>
        </span>
      </div>
    </div>
  );
}

function PresenceScene() {
  return (
    <div className="ws-scene ws-presence">
      <div className="ws-person" style={{ ['--d' as string]: '0.1s' }}>
        <span className="ws-avatar ws-avatar--md ws-avatar--a">M</span>
        <span className="ws-dot ws-dot--on" />
        <div className="ws-person-text">
          <strong>Minh</strong>
          <span className="ws-status ws-status--on">Online</span>
        </div>
      </div>
      <div className="ws-person" style={{ ['--d' as string]: '0.6s' }}>
        <span className="ws-avatar ws-avatar--md ws-avatar--b">L</span>
        <span className="ws-dot" />
        <div className="ws-person-text">
          <strong>Lan</strong>
          <span className="ws-status">Active 5 minutes ago</span>
        </div>
      </div>
      <div className="ws-person" style={{ ['--d' as string]: '1.1s' }}>
        <span className="ws-avatar ws-avatar--md ws-avatar--c">T</span>
        <span className="ws-dot ws-dot--on" />
        <div className="ws-person-text">
          <strong>Tuan</strong>
          <span className="ws-status ws-status--on">Online</span>
        </div>
      </div>
    </div>
  );
}

const SCENE_VIEWS = [ChatScene, ShareScene, CallScene, PresenceScene];

/** Animated welcome screen shown when no conversation is open. */
export function WelcomeShowcase({ onStartChat }: WelcomeShowcaseProps) {
  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);
  const reduced = prefersReducedMotion();

  useEffect(() => {
    if (paused || reduced) return;
    const id = setTimeout(() => setIndex((i) => (i + 1) % SCENES.length), SCENE_MS);
    return () => clearTimeout(id);
  }, [index, paused, reduced]);

  const Scene = SCENE_VIEWS[index];
  const scene = SCENES[index];

  return (
    <div className="welcome" onMouseEnter={() => setPaused(true)} onMouseLeave={() => setPaused(false)}>
      <div className="welcome-stage" aria-live="off">
        <span className="welcome-glow welcome-glow--1" aria-hidden="true" />
        <span className="welcome-glow welcome-glow--2" aria-hidden="true" />
        <div className="welcome-scene" key={scene.key}>
          <Scene />
        </div>
        <span className="welcome-scene-label">{scene.label}</span>
      </div>

      <div className="welcome-dots" role="tablist" aria-label="Feature highlights">
        {SCENES.map((s, i) => (
          <button
            key={s.key}
            role="tab"
            aria-selected={i === index}
            aria-label={s.label}
            className={`welcome-dot${i === index ? ' welcome-dot--active' : ''}`}
            onClick={() => setIndex(i)}
          />
        ))}
      </div>

      <h2 className="welcome-title">Welcome to Messenger Mini</h2>
      <p className="welcome-text">{scene.caption}</p>
      <button className="welcome-cta" onClick={onStartChat}>
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M12 5v14M5 12h14" />
        </svg>
        Start a new chat
      </button>
    </div>
  );
}
