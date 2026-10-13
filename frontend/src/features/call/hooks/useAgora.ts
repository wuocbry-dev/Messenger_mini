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
  ILocalVideoTrack,
  IMicrophoneAudioTrack,
  IRemoteAudioTrack,
  IRemoteVideoTrack,
} from 'agora-rtc-sdk-ng';

export type CallMedia = 'VOICE' | 'VIDEO';

export interface AgoraState {
  phase: 'idle' | 'requesting' | 'joining' | 'joined' | 'ended' | 'error';
  localMuted: boolean;
  speakerOn: boolean;
  cameraOn: boolean;
  /** Sharing the screen instead of the camera. */
  sharing: boolean;
  remoteJoined: boolean;
  /** The video currently published (camera or screen). */
  localVideo: ICameraVideoTrack | ILocalVideoTrack | null;
  remoteVideo: IRemoteVideoTrack | null;
  error?: string;
  /** Set when a video call fell back to audio only (e.g. camera denied). */
  notice?: string;
}

const INITIAL: AgoraState = {
  phase: 'idle',
  localMuted: false,
  speakerOn: true,
  cameraOn: false,
  sharing: false,
  remoteJoined: false,
  localVideo: null,
  remoteVideo: null,
};

export function useAgora() {
  const [state, setState] = useState<AgoraState>(INITIAL);
  const clientRef = useRef<IAgoraRTCClient | null>(null);
  const audioRef = useRef<IMicrophoneAudioTrack | null>(null);
  const videoRef = useRef<ICameraVideoTrack | null>(null);
  const screenRef = useRef<ILocalVideoTrack | null>(null);
  const remoteAudioRef = useRef<IRemoteAudioTrack | null>(null);
  const speakerOnRef = useRef(true);
  const busyRef = useRef(false);

  const teardown = useCallback(async () => {
    const audio = audioRef.current;
    const video = videoRef.current;
    const screen = screenRef.current;
    const client = clientRef.current;
    audioRef.current = null;
    videoRef.current = null;
    screenRef.current = null;
    remoteAudioRef.current = null;
    speakerOnRef.current = true;
    clientRef.current = null;
    busyRef.current = false;
    for (const track of [audio, video, screen]) {
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
            remoteAudioRef.current = user.audioTrack ?? null;
            user.audioTrack?.setVolume(speakerOnRef.current ? 100 : 0);
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
    // setMuted keeps the microphone device open, so unmuting is instant.
    const nextMuted = !track.muted;
    await track.setMuted(nextMuted);
    setState((prev) => ({ ...prev, localMuted: nextMuted }));
  }, []);

  const toggleSpeaker = useCallback(() => {
    const next = !speakerOnRef.current;
    speakerOnRef.current = next;
    remoteAudioRef.current?.setVolume(next ? 100 : 0);
    setState((prev) => ({ ...prev, speakerOn: next }));
  }, []);

  const toggleCamera = useCallback(async () => {
    const track = videoRef.current;
    if (!track) return;
    const on = !track.enabled;
    await track.setEnabled(on);
    setState((prev) => ({ ...prev, cameraOn: on }));
  }, []);

  /** Swap the published video between the camera and a shared screen. */
  const toggleScreenShare = useCallback(async () => {
    const client = clientRef.current;
    if (!client) return;
    const camera = videoRef.current;

    if (screenRef.current) {
      const screen = screenRef.current;
      screenRef.current = null;
      await client.unpublish(screen).catch(() => {});
      screen.stop();
      screen.close();
      if (camera) await client.publish(camera);
      setState((prev) => ({ ...prev, sharing: false, localVideo: camera, cameraOn: camera?.enabled ?? false }));
      return;
    }

    try {
      const AgoraRTC = (await import('agora-rtc-sdk-ng')).default;
      const created = await AgoraRTC.createScreenVideoTrack({ encoderConfig: '1080p_1' }, 'disable');
      const screen = Array.isArray(created) ? created[0] : created;
      screenRef.current = screen;
      if (camera) await client.unpublish(camera).catch(() => {});
      await client.publish(screen);
      // The browser's own "Stop sharing" button ends the track.
      screen.on('track-ended', () => void toggleScreenShare());
      setState((prev) => ({ ...prev, sharing: true, localVideo: screen, cameraOn: true }));
    } catch (err) {
      // Cancelling the picker is not an error worth surfacing.
      if (err instanceof Error && err.name !== 'NotAllowedError' && !/denied|cancel/i.test(err.message)) {
        setState((prev) => ({ ...prev, notice: 'Could not start screen sharing' }));
      }
    }
  }, []);

  const listCameras = useCallback(async () => {
    const AgoraRTC = (await import('agora-rtc-sdk-ng')).default;
    const devices = await AgoraRTC.getCameras().catch(() => []);
    return devices.map((d) => ({ id: d.deviceId, label: d.label || 'Camera' }));
  }, []);

  const switchCamera = useCallback(async (deviceId: string) => {
    await videoRef.current?.setDevice(deviceId).catch(() => {});
  }, []);

  useEffect(() => () => void teardown(), [teardown]);

  return {
    state,
    join,
    leave,
    toggleMute,
    toggleCamera,
    toggleSpeaker,
    toggleScreenShare,
    listCameras,
    switchCamera,
  };
}
