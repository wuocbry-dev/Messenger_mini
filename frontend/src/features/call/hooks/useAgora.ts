/**
 * useAgora — manages the Agora RTC Web SDK lifecycle for voice calls.
 *
 * Responsibilities:
 * - Request microphone permission
 * - Join / leave channel
 * - Publish local audio (mute/unmute)
 * - Subscribe to remote audio
 * - Cleanup on unmount
 *
 * The call token is fetched from the backend; this hook does not generate tokens.
 */
import { useRef, useState, useCallback, useEffect } from 'react';
import type { IAgoraRTCClient, ILocalAudioTrack, IMicrophoneAudioTrack } from 'agora-rtc-sdk-ng';

export interface AgoraState {
  phase: 'idle' | 'requesting' | 'joining' | 'joined' | 'ended' | 'error';
  localMuted: boolean;
  remoteJoined: boolean;
  error?: string;
}

export function useAgora() {
  const [state, setState] = useState<AgoraState>({
    phase: 'idle',
    localMuted: false,
    remoteJoined: false,
  });
  const clientRef = useRef<IAgoraRTCClient | null>(null);
  const localTrackRef = useRef<ILocalAudioTrack | null>(null);
  const joinedRef = useRef(false);

  const setPhase = useCallback((phase: AgoraState['phase'], error?: string) => {
    setState(prev => ({ ...prev, phase, error }));
  }, []);

  /**
   * Join an Agora channel. `token` and `appId` come from the backend.
   * `VITE_AGORA_APP_ID` (public value) overrides the backend App ID when set.
   */
  const join = useCallback(async (backendAppId: string, channelName: string, uid: number, token: string) => {
    // Set before any await so a second, concurrent join (e.g. a duplicated
    // ACCEPTED event) can't create another client with the same uid.
    if (joinedRef.current) return;
    joinedRef.current = true;

    const envAppId = import.meta.env.VITE_AGORA_APP_ID?.trim();
    const appId = envAppId || backendAppId;
    if (envAppId && backendAppId && envAppId !== backendAppId) {
      // The token is signed by the backend for its own App ID, so a mismatch
      // makes Agora reject the join.
      console.warn(
        `VITE_AGORA_APP_ID (${envAppId}) differs from backend AGORA_APP_ID (${backendAppId}); the token will not match.`
      );
    }

    setPhase('requesting');

    try {
      // Dynamic import to code-split the heavy SDK
      const AgoraRTC = (await import('agora-rtc-sdk-ng')).default;

      // Create microphone track
      const localTrack = await AgoraRTC.createMicrophoneAudioTrack({
        AEC: true,
        ANS: true,
        AGC: true,
      }) as IMicrophoneAudioTrack;
      localTrackRef.current = localTrack;

      // Create client
      const client = AgoraRTC.createClient({
        codec: 'vp8',
        mode: 'rtc',
      }) as IAgoraRTCClient;
      clientRef.current = client;

      // Set up remote user events
      client.on('user-published', async (user: { uid: number | string; hasAudio: boolean }, mediaType: string) => {
        if (mediaType === 'audio') {
          await client.subscribe(user as Parameters<typeof client.subscribe>[0], mediaType as Parameters<typeof client.subscribe>[1]);
          setState(prev => ({ ...prev, remoteJoined: true }));
        }
      });

      client.on('user-unpublished', (user: { uid: number | string; hasAudio: boolean }, mediaType: string) => {
        if (mediaType === 'audio') {
          setState(prev => ({ ...prev, remoteJoined: false }));
        }
      });

      setPhase('joining');

      // Join the channel
      await client.join(appId, channelName, token, uid);

      // Publish local audio
      await client.publish(localTrack);

      setState(prev => ({ ...prev, phase: 'joined' }));

    } catch (err) {
      console.error('Agora join failed', err);
      setPhase('error', err instanceof Error ? err.message : 'Failed to join call');
      localTrackRef.current?.close();
      localTrackRef.current = null;
      clientRef.current = null;
      joinedRef.current = false;
    }
  }, [setPhase]);

  const leave = useCallback(async () => {
    const localTrack = localTrackRef.current;
    const client = clientRef.current;

    if (localTrack) {
      localTrack.stop();
      localTrack.close();
      localTrackRef.current = null;
    }

    if (client) {
      try { await client.leave(); } catch { /* ignore */ }
      clientRef.current = null;
    }

    joinedRef.current = false;
    setState({ phase: 'ended', localMuted: false, remoteJoined: false });
  }, []);

  const toggleMute = useCallback(async () => {
    const track = localTrackRef.current;
    if (!track) return;
    const muted = !state.localMuted;
    if (muted) {
      (track as IMicrophoneAudioTrack).setEnabled(false);
    } else {
      (track as IMicrophoneAudioTrack).setEnabled(true);
    }
    setState(prev => ({ ...prev, localMuted: muted }));
  }, [state.localMuted]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      localTrackRef.current?.stop();
      localTrackRef.current?.close();
      clientRef.current?.leave().catch(() => {});
      joinedRef.current = false;
    };
  }, []);

  return { state, join, leave, toggleMute };
}
