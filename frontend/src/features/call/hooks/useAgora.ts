/**
 * useAgora — manages the Agora RTC Web SDK lifecycle for voice and video
 * calls with one or many remote people.
 *
 * The token and App ID come from the backend; the uid must be the numeric
 * user id the token was issued for, so remote uids are user ids.
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
  /** True while at least one other person is connected to the channel. */
  remoteJoined: boolean;
  /** User ids (uids) of the people currently connected. */
  remoteUids: number[];
  /** The video currently published (camera or screen). */
  localVideo: ICameraVideoTrack | ILocalVideoTrack | null;
  /** Remote video tracks by user id. */
  remoteVideos: Record<number, IRemoteVideoTrack>;
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
  remoteUids: [],
  localVideo: null,
  remoteVideos: {},
};

export function useAgora() {
  const [state, setState] = useState<AgoraState>(INITIAL);
  const clientRef = useRef<IAgoraRTCClient | null>(null);
  const audioRef = useRef<IMicrophoneAudioTrack | null>(null);
  const videoRef = useRef<ICameraVideoTrack | null>(null);
  const screenRef = useRef<ILocalVideoTrack | null>(null);
  const remoteAudioRef = useRef<Map<number, IRemoteAudioTrack>>(new Map());
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
    remoteAudioRef.current = new Map();
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

  const addRemoteUid = (uid: number) =>
    setState((prev) => {
      if (prev.remoteUids.includes(uid)) return prev;
      const remoteUids = [...prev.remoteUids, uid];
      return { ...prev, remoteUids, remoteJoined: true };
    });

  const removeRemoteUid = (uid: number) => {
    remoteAudioRef.current.delete(uid);
    setState((prev) => {
      const remoteUids = prev.remoteUids.filter((id) => id !== uid);
      const { [uid]: _gone, ...remoteVideos } = prev.remoteVideos;
      return { ...prev, remoteUids, remoteJoined: remoteUids.length > 0, remoteVideos };
    });
  };

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

        client.on('user-joined', (user) => addRemoteUid(Number(user.uid)));
        client.on('user-published', async (user, mediaType) => {
          const remoteUid = Number(user.uid);
          await client.subscribe(user, mediaType);
          addRemoteUid(remoteUid);
          if (mediaType === 'audio' && user.audioTrack) {
            remoteAudioRef.current.set(remoteUid, user.audioTrack);
            user.audioTrack.setVolume(speakerOnRef.current ? 100 : 0);
            user.audioTrack.play();
          } else if (mediaType === 'video' && user.videoTrack) {
            const remoteVideo = user.videoTrack;
            setState((prev) => ({
              ...prev,
              remoteVideos: { ...prev.remoteVideos, [remoteUid]: remoteVideo },
            }));
          }
        });
        client.on('user-unpublished', (user, mediaType) => {
          if (mediaType === 'video') {
            const remoteUid = Number(user.uid);
            setState((prev) => {
              const { [remoteUid]: _gone, ...remoteVideos } = prev.remoteVideos;
              return { ...prev, remoteVideos };
            });
          }
        });
        client.on('user-left', (user) => removeRemoteUid(Number(user.uid)));

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
    remoteAudioRef.current.forEach((track) => track.setVolume(next ? 100 : 0));
    setState((prev) => ({ ...prev, speakerOn: next }));
  }, []);

  /** Starts the camera and publishes it (used when a voice call turns into video). */
  const enableCamera = useCallback(async (): Promise<boolean> => {
    const client = clientRef.current;
    if (!client) return false;
    if (videoRef.current) {
      await videoRef.current.setEnabled(true);
      setState((prev) => ({ ...prev, cameraOn: true, localVideo: prev.sharing ? prev.localVideo : videoRef.current }));
      return true;
    }
    try {
      const AgoraRTC = (await import('agora-rtc-sdk-ng')).default;
      const camera = await AgoraRTC.createCameraVideoTrack({ encoderConfig: '480p_1' });
      videoRef.current = camera;
      if (!screenRef.current) await client.publish(camera);
      setState((prev) => ({
        ...prev,
        cameraOn: true,
        notice: undefined,
        localVideo: prev.sharing ? prev.localVideo : camera,
      }));
      return true;
    } catch {
      setState((prev) => ({ ...prev, notice: 'Camera unavailable or permission denied' }));
      return false;
    }
  }, []);

  /** Toggles the camera; creates it first if the call started without one. */
  const toggleCamera = useCallback(async () => {
    const track = videoRef.current;
    if (!track) {
      await enableCamera();
      return;
    }
    const on = !track.enabled;
    await track.setEnabled(on);
    setState((prev) => ({ ...prev, cameraOn: on }));
  }, [enableCamera]);

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
    enableCamera,
    toggleSpeaker,
    toggleScreenShare,
    listCameras,
    switchCamera,
  };
}
