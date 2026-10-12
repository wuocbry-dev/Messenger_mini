# Voice Call

The approved initial voice-call scope is 1-to-1 voice call. Video call is a future optional extension.

Messenger Mini uses Agora RTC for realtime media transport.

## Frontend Responsibilities

- Use the Agora Web SDK.
- Request microphone permission.
- Join the Agora channel.
- Publish local audio.
- Subscribe to remote audio.
- Support mute/unmute.
- Leave the call.

## Spring Boot Responsibilities

- Authorize call actions.
- Create and manage call business state.
- Handle call request, accept, reject, and end flows.
- Generate Agora channel names.
- Generate temporary Agora RTC tokens.
- Store call history.

## Agora Responsibilities

- Realtime media transport.
- Codec and media networking.
- NAT traversal and relay.
- Realtime voice infrastructure.

## MySQL `calls` Table

The `calls` table stores business and history data such as participants, status, channel name, start time, answer time, end time, and end reason.

Do not store:

- Agora App Certificate.
- Temporary Agora RTC tokens.
