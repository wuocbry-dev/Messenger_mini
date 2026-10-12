Implement 1-to-1 Voice Call business logic using Agora RTC.

Read docs/VOICE_CALL.md first.

IMPORTANT:

Agora handles realtime media.
Spring Boot does NOT proxy audio.

Backend responsibilities:

- authorization
- start call
- incoming-call signaling
- accept
- reject
- cancel
- end
- Agora channel naming
- Agora RTC token generation
- call history

Required call statuses:

RINGING
ACCEPTED
REJECTED
MISSED
CANCELLED
ENDED
FAILED

Create REST/realtime contract appropriate for:

POST /api/v1/calls
POST /api/v1/calls/{id}/accept
POST /api/v1/calls/{id}/reject
POST /api/v1/calls/{id}/cancel
POST /api/v1/calls/{id}/end
POST /api/v1/calls/{id}/token or equivalent secure design

GET /api/v1/calls

Rules:

- only conversation participants can call each other
- caller != callee
- channel name generated server-side
- Agora App Certificate backend-only
- Agora token temporary and never persisted
- calls table stores business/history data only
- validate legal state transitions
- prevent unauthorized call actions

Realtime incoming call events may use existing WebSocket/STOMP channel.

Add AgoraTokenService implementation.

Tests must mock token generation/external integration when possible and
must not require real paid/external calls.

Run clean verify.

Do not implement Video Call; keep future compatibility only.