import React, { useCallback, useState } from 'react';
import { FeatureShowcase, SHOWCASE_SCENES, type ShowcaseScene } from '../../../../shared/components/FeatureShowcase';
import './WelcomeShowcase.css';

interface WelcomeShowcaseProps {
  onStartChat: () => void;
}

/** Animated welcome screen shown when no conversation is open. */
export function WelcomeShowcase({ onStartChat }: WelcomeShowcaseProps) {
  const [caption, setCaption] = useState<string>(SHOWCASE_SCENES[0].caption);
  const onSceneChange = useCallback((scene: ShowcaseScene) => setCaption(scene.caption), []);

  return (
    <div className="welcome">
      <FeatureShowcase onSceneChange={onSceneChange} />

      <h2 className="welcome-title">Welcome to Messenger Mini</h2>
      <p className="welcome-text">{caption}</p>
      <button className="welcome-cta" onClick={onStartChat}>
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M12 5v14M5 12h14" />
        </svg>
        Start a new chat
      </button>
    </div>
  );
}
