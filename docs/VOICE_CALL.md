# Voice Call

The approved initial voice-call scope is 1-to-1 voice call. Video call is a future optional extension.

Messenger Mini uses Agora RTC for realtime media transport.

## Frontend Responsibilities

- Use the Agora Web SDK.
    10|- Request microphone permission.
- Join the Agora channel.
- Publish local audio.
- Subscribe to remote audio.
- Support mute/unmute.
- Leave the call.

## Spring Boot Responsibilities

- Authorize call actions.
    20|- Create and manage call business state.
- Handle call request, accept, reject, and end flows.
- Generate Agora channel names.
- Generate temporary Agora RTC tokens.
- Store call history.

## Agora Responsibilities

- Realtime media transport.
- Codec and media networking.
    30|- NAT traversal and relay.
- Realtime voice infrastructure.

## MySQL `calls` Table

The `calls` table stores business and history data such as participants, status, channel name, start time, answer time, end time, and end reason.

Do not store:

- Agora App Certificate.
    40|- Temporary Agora RTC tokens.

---

## Call lifecycle (task 009)

The call state machine lives in `Call` (entity) and `CallService`.

| State | Reachable from | Triggered by |
|---|---|---|
| `RINGING` | (initial) | `POST /api/v1/calls` |
| `ACCEPTED` | `RINGING` | `POST /api/v1/calls/{id}/accept` (callee) |
| `REJECTED` | `RINGING` | `POST /api/v1/calls/{id}/reject` (callee) |
| `CANCELLED` | `RINGING` | `POST /api/v1/calls/{id}/cancel` (caller) |
| `ENDED`    | `ACCEPTED` | `POST /api/v1/calls/{id}/end` (caller or callee) |
| `MISSED`   | `RINGING` | scheduled timeout (out of scope for this task) |
| `FAILED`   | any non-terminal | internal error path |

`REJECTED`, `MISSED`, `CANCELLED`, `ENDED`, `FAILED` are terminal. The
service rejects any state transition out of a terminal state.

## REST endpoints (task 009)

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/calls` | Start a call (caller, conversation, callee). |
| `POST` | `/api/v1/calls/{id}/accept` | Callee accepts. |
| `POST` | `/api/v1/calls/{id}/reject` | Callee rejects. |
| `POST` | `/api/v1/calls/{id}/cancel` | Caller cancels while still ringing. |
| `POST` | `/api/v1/calls/{id}/end` | Either participant ends an accepted call. |
| `POST` | `/api/v1/calls/{id}/token` | Issue a temporary Agora RTC token. |
| `GET`  | `/api/v1/calls/{id}` | Get one call. |
| `GET`  | `/api/v1/calls` | Call history for the current user. |
| `GET`  | `/api/v1/calls/conversations/{conversationId}` | Call history for a conversation. |

### Token response

```json
{
  "callId": 42,
  "agoraChannelName": "call-1-abc123def456",
  "token": "<short-lived JWT-style token>",
  "appId": "<public Agora App ID>",
  "expiresInSeconds": 600
}
```

The App Certificate is never returned; the App ID is public.

## Realtime signaling (STOMP)

| Direction | Destination | Payload | Notes |
|---|---|---|---|
| S → C | `/user/{username}/queue/calls` | `CallEvent` JSON | Published on every state transition to the relevant participants' user queues. |

```json
{
  "type": "INCOMING",
  "callId": 42,
  "conversationId": 1,
  "callerId": 1,
  "calleeId": 2,
  "callType": "VOICE",
  "status": "RINGING",
  "agoraChannelName": "call-1-abc123def456",
  "startedAt": "2026-10-12T12:34:56.789",
  "answeredAt": null,
  "endedAt": null,
  "endReason": null
}
```

`type` is one of `INCOMING`, `ACCEPTED`, `REJECTED`, `CANCELLED`, `ENDED`.

## Authorization rules

- Only active members of the conversation may start a call.
- The caller must be an active member; the callee must also be an active
  member of the same conversation.
- `caller != callee` is enforced both in the service and in the entity.
- Only the callee may accept or reject; only the caller may cancel; only
  participants may end.
- A conversation can have at most one active (`RINGING` or `ACCEPTED`) call
  at a time.
- Token issuance is restricted to call participants; the call must be
  `RINGING` or `ACCEPTED`.

## Backend environment variables

```text
AGORA_APP_ID
AGORA_APP_CERTIFICATE   # backend-only, never exposed
AGORA_TOKEN_TTL_SECONDS (default 600)
```

## Channel name policy

- Server-generated; clients never supply the channel name.
- Format: `call-<conversationId>-<16 hex chars>`.
- The conversation id is included for log correlation; the random token
  ensures channel names cannot be guessed.

## Security guarantees

- The Agora App Certificate is read from environment variables and is held
  only by `DefaultAgoraTokenService`. It is never returned in any HTTP
  response, never logged, and never persisted in MySQL.
- Agora tokens are short-lived (default 600s) and are never persisted.
- Call state changes publish only to user queues of the caller and callee
  — no third-party observer can read call metadata via STOMP.
- Spring Boot does not proxy audio; the frontend joins the Agora channel
  using the token issued by the backend.

## Out of scope for task 009

- Missed-call timeout: transitioning `RINGING` calls to `MISSED` after N
  seconds. The `missed` status and the `markMissed` method exist on the
  entity, but a scheduled job to drive the transition is left for a later
  task.
- Call history retention policy.
- Video call. The `CallType.VIDEO` enum value exists for future
  compatibility; the `startCall` flow currently only allows `VOICE`.
- Group calls. The current implementation is 1-to-1 only and enforces
  exactly two participants.
