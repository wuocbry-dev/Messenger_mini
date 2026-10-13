/**
 * useAgora — manages the Agora RTC Web SDK lifecycle for 1-to-1 voice calls.
 *
 * The token and App ID come from the backend; the uid must be the numeric
 * user id the token was issued for.
 */
import { useRef, useState, useCallback, useEffect } from 'react';
import type { IAgoraRTCClient, IMicrophoneAudioTrack } from 'agora-rtc-sdk-ng';

export interface AgoraState {
  phase: 'idle' | 'requesting' | 'joining' | 'joined' | 'ended' | 'error';
  localMuted: boolean;
  remoteJoined: boolean;
  error?: string;
}

const INITIAL: AgoraState = { phase: 'idle', localMuted: false, remoteJoined: false };

export function useAgora() {
  const [state, setState] = useState<AgoraState>(INITIAL);
  const clientRef = useRef<IAgoraRTCClient | null>(null);
  const trackRef = useRef<IMicrophoneAudioTrack | null>(null);
  const busyRef = useRef(false);

  const teardown = useCallback(async () => {
    const track = trackRef.current;
    const client = clientRef.current;
    trackRef.current = null;
    clientRef.current = null;
    busyRef.current = false;
    if (track) {
      track.stop();
      track.close();
    }
    if (client) {
      client.removeAllListeners();
      try {
        await client.leave();
      } catch {
        /* already left */
      }
    }
  }, []);

  const join = useCallback(
    async (appId: string, channelName: string, uid: number, token: string) => {
      if (busyRef.current) return;
      busyRef.current = true;
      setState({ ...INITIAL, phase: 'requesting' });

      try {
        const AgoraRTC = (await import('agora-rtc-sdk-ng')).default;
        AgoraRTC.setLogLevel(3);

        const track = await AgoraRTC.createMicrophoneAudioTrack({ AEC: true, ANS: true, AGC: true });
        trackRef.current = track;

        const client = AgoraRTC.createClient({ mode: 'rtc', codec: 'vp8' });
        clientRef.current = client;

        client.on('user-published', async (user, mediaType) => {
          if (mediaType !== 'audio') return;
          await client.subscribe(user, 'audio');
          user.audioTrack?.play();
          setState((prev) => ({ ...prev, remoteJoined: true }));
        });
        client.on('user-unpublished', (_user, mediaType) => {
          if (mediaType === 'audio') setState((prev) => ({ ...prev, remoteJoined: false }));
        });
        client.on('user-left', () => setState((prev) => ({ ...prev, remoteJoined: false })));

        setState((prev) => ({ ...prev, phase: 'joining' }));
        await client.join(appId, channelName, token, uid);
        await client.publish(track);
        setState((prev) => ({ ...prev, phase: 'joined' }));
      } catch (err) {
        await teardown();
        const message =
          err instanceof Error && err.name === 'NotAllowedError'
            ? 'Microphone permission was denied'
            : err instanceof Error
              ? err.message
              : 'Failed to join call';
        setState({ ...INITIAL, phase: 'error', error: message });
      }
    },
    [teardown]
  );

  const leave = useCallback(async () => {
    await teardown();
    setState({ ...INITIAL, phase: 'ended' });
  }, [teardown]);

  const toggleMute = useCallback(async () => {
    const track = trackRef.current;
    if (!track) return;
    const muted = !track.enabled;
    await track.setEnabled(!muted);
    setState((prev) => ({ ...prev, localMuted: muted }));
  }, []);

  useEffect(() => () => void teardown(), [teardown]);

  return { state, join, leave, toggleMute };
}
