/**
 * useAgora — manages the Agora RTC Web SDK lifecycle for 1-to-1 voice and
 * video calls.
 *
 * The token and App ID come from the backend; the uid must be the numeric
 * user id the token was issued for.
 */
import { useRef, useState, useCallback, useEffect } from 'react';
import type {
  IAgoraRTCClient,
  ICameraVideoTrack,
  IMicrophoneAudioTrack,
  IRemoteVideoTrack,
} from 'agora-rtc-sdk-ng';

export type CallMedia = 'VOICE' | 'VIDEO';

export interface AgoraState {
  phase: 'idle' | 'requesting' | 'joining' | 'joined' | 'ended' | 'error';
  localMuted: boolean;
  cameraOn: boolean;
  remoteJoined: boolean;
  localVideo: ICameraVideoTrack | null;
  remoteVideo: IRemoteVideoTrack | null;
  error?: string;
  /** Set when a video call fell back to audio only (e.g. camera denied). */
  notice?: string;
}

const INITIAL: AgoraState = {
  phase: 'idle',
  localMuted: false,
  cameraOn: false,
  remoteJoined: false,
  localVideo: null,
  remoteVideo: null,
};

export function useAgora() {
  const [state, setState] = useState<AgoraState>(INITIAL);
  const clientRef = useRef<IAgoraRTCClient | null>(null);
  const audioRef = useRef<IMicrophoneAudioTrack | null>(null);
  const videoRef = useRef<ICameraVideoTrack | null>(null);
  const busyRef = useRef(false);

  const teardown = useCallback(async () => {
    const audio = audioRef.current;
    const video = videoRef.current;
    const client = clientRef.current;
    audioRef.current = null;
    videoRef.current = null;
    clientRef.current = null;
    busyRef.current = false;
    for (const track of [audio, video]) {
      if (track) {
        track.stop();
        track.close();
      }
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
    async (
      appId: string,
      channelName: string,
      uid: number,
      token: string,
      media: CallMedia = 'VOICE'
    ) => {
      if (busyRef.current) return;
      busyRef.current = true;
      setState({ ...INITIAL, phase: 'requesting' });

      try {
        const AgoraRTC = (await import('agora-rtc-sdk-ng')).default;
        AgoraRTC.setLogLevel(3);

        const audio = await AgoraRTC.createMicrophoneAudioTrack({ AEC: true, ANS: true, AGC: true });
        audioRef.current = audio;

        let notice: string | undefined;
        if (media === 'VIDEO') {
          try {
            videoRef.current = await AgoraRTC.createCameraVideoTrack({
              encoderConfig: '480p_1',
            });
          } catch {
            notice = 'Camera unavailable — continuing with audio only';
          }
        }

        const client = AgoraRTC.createClient({ mode: 'rtc', codec: 'vp8' });
        clientRef.current = client;

        client.on('user-published', async (user, mediaType) => {
          await client.subscribe(user, mediaType);
          if (mediaType === 'audio') {
            user.audioTrack?.play();
            setState((prev) => ({ ...prev, remoteJoined: true }));
          } else if (user.videoTrack) {
            const remoteVideo = user.videoTrack;
            setState((prev) => ({ ...prev, remoteJoined: true, remoteVideo }));
          }
        });
        client.on('user-unpublished', (_user, mediaType) => {
          if (mediaType === 'audio') setState((prev) => ({ ...prev, remoteJoined: false }));
          else setState((prev) => ({ ...prev, remoteVideo: null }));
        });
        client.on('user-left', () =>
          setState((prev) => ({ ...prev, remoteJoined: false, remoteVideo: null }))
        );

        setState((prev) => ({ ...prev, phase: 'joining' }));
        await client.join(appId, channelName, token, uid);
        await client.publish(videoRef.current ? [audio, videoRef.current] : [audio]);
        setState((prev) => ({
          ...prev,
          phase: 'joined',
          cameraOn: videoRef.current !== null,
          localVideo: videoRef.current,
          notice,
        }));
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
    const track = audioRef.current;
    if (!track) return;
    const muted = !track.enabled;
    await track.setEnabled(!muted);
    setState((prev) => ({ ...prev, localMuted: muted }));
  }, []);

  const toggleCamera = useCallback(async () => {
    const track = videoRef.current;
    if (!track) return;
    const on = !track.enabled;
    await track.setEnabled(on);
    setState((prev) => ({ ...prev, cameraOn: on }));
  }, []);

  useEffect(() => () => void teardown(), [teardown]);

  return { state, join, leave, toggleMute, toggleCamera };
}
