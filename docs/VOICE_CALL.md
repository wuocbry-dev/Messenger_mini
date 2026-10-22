# Voice Call

Calls are 1-to-1 and come in two types: `VOICE` and `VIDEO`. Both use the same
lifecycle, endpoints, channel and token; only the media the client publishes differs.

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
| `MISSED`   | `RINGING` | `CallTimeoutScheduler` after `app.agora.ring-timeout-seconds` (default 45) |
| `FAILED`   | any non-terminal | internal error path |

`REJECTED`, `MISSED`, `CANCELLED`, `ENDED`, `FAILED` are terminal. The
service rejects any state transition out of a terminal state.

## REST endpoints (task 009)

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/calls` | Start a call. Body: `conversationId`, `calleeId`, optional `callType` (`VOICE` default, or `VIDEO`). |
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
  "token": "<Agora AccessToken2, prefix 007>",
  "appId": "<public Agora App ID>",
  "uid": 7,
  "expiresInSeconds": 600
}
```

The App Certificate is never returned; the App ID is public.

Tokens are built in-process by `DefaultAgoraTokenService` in the Agora
AccessToken2 ("007") format (RTC service, join + publish-audio privileges).
The client must join with the numeric user id as `uid`, because the token is
bound to that uid and the channel name. The earlier hand-rolled JWT-style
token was not valid for Agora and was replaced.

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

`type` is one of `INCOMING`, `ACCEPTED`, `REJECTED`, `CANCELLED`, `MISSED`, `ENDED`. Events are published after the DB transaction commits. The caller also receives `INCOMING` (multi-device sync) and must not ring itself.

## Authorization rules

- Only active members of the conversation may start a call.
- The caller must be an active member; the callee must also be an active
  member of the same conversation.
- `caller != callee` is enforced both in the service and in the entity.
- Only the callee may accept or reject; only the caller may cancel; only
  participants may end.
- A conversation can have at most one active (`RINGING` or `ACCEPTED`) call
  at a time.
- Token issuance is restricted to participants. The caller may get a token while
  `RINGING` or `ACCEPTED`; the callee only once `ACCEPTED`.
- A user can be in at most one `RINGING`/`ACCEPTED` call (`CALLER_BUSY`/`CALLEE_BUSY`).

## Backend environment variables

```text
AGORA_APP_ID
AGORA_APP_CERTIFICATE   # backend-only, never exposed
AGORA_TOKEN_TTL_SECONDS (default 600)
AGORA_RING_TIMEOUT_SECONDS (default 45)
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

- Call history retention policy.

## Video calls

- `callType` is stored on the call and carried in every `CallEvent`, so the
  callee's incoming prompt and the join logic know whether to publish video.
- The RTC token always grants join, publish-audio and publish-video
  privileges; the call type is not enforced by the token.
- Frontend: a `VIDEO` call creates a camera track in addition to the
  microphone. If the camera is unavailable the call continues audio-only with
  a notice. The camera can be toggled during the call.
- Media still flows through Agora only; Spring Boot never proxies it.

## Group calls

A call has one row in `calls` and one row per person in `call_participants`
(migration `V2__group_calls.sql`). Up to **8 people** (`CallService.MAX_PARTICIPANTS`)
can be in a call, the caller included.

### Participants

| State | Meaning |
|---|---|
| `INVITED` | Being rung. |
| `JOINED` | In the call (the caller starts here). |
| `DECLINED` | Rejected the call. |
| `LEFT` | Was in the call and left. |
| `MISSED` | Never answered (timeout, or the caller hung up first). |

`calls.callee_id` is only set for 1-to-1 calls and is `NULL` when the call was
started in a group conversation.

### Call status rules

- `RINGING` until the first invitee answers, then `ACCEPTED`.
- `REJECTED` when every invitee declined while still `RINGING`; one decline in
  a group does not end the call.
- `CANCELLED` when the caller hangs up while `RINGING`; `MISSED` after
  `app.agora.ring-timeout-seconds`.
- A running call ends (`ENDED`) when fewer than two people remain `JOINED`.
  People added to a running call who never answer are marked `MISSED`.

### Starting and adding people

- `POST /api/v1/calls` takes `conversationId`, an optional `calleeId` and
  `callType`. In a group conversation omit `calleeId`: every other member is
  rung (people already in another call are skipped).
- `POST /api/v1/calls/{id}/invite` with `{ "userIds": [...] }` rings more
  people. Only someone who is `JOINED` may invite.
  - In a **1-to-1** call a new **group conversation** with everyone in the call
    is created and the call moves to it (`calls.conversation_id` changes).
  - In a **group** call the invited people are added to that group chat.
- `POST /api/v1/calls/{id}/video` switches a voice call to video
  (`calls.call_type` becomes `VIDEO`). Each person still opts in with their
  camera.

### Events

Every `CallEvent` carries `actorId` and the full `participants` list
(`userId`, `username`, `displayName`, `avatarUrl`, `state`), so a client can
rebuild the call from any single event. Types:

`INCOMING` (only to people being rung), `ACCEPTED` (first answer),
`REJECTED`, `CANCELLED`, `MISSED`, `ENDED`, `PARTICIPANT_INVITED`,
`PARTICIPANT_JOINED`, `PARTICIPANT_LEFT`, `PARTICIPANT_DECLINED`,
`MEDIA_UPGRADED`, `CONVERSATION_CHANGED`.

### Group chats

- `POST /api/v1/conversations/group` (`title` optional, `memberIds` at least 2
  others) creates a group conversation owned by the caller.
- `POST /api/v1/conversations/{id}/members` adds people to a group; direct
  conversations cannot grow.
- Groups hold up to 50 people.

### Known limits

- A call whose participants close their tabs without leaving stays
  `ACCEPTED` until someone leaves; there is no server-side cleanup yet
  (the migration closes such calls older than a day).
- Agora media is routed by Agora; the backend never proxies it.

## Call history in the chat

Every finished call is also recorded in its conversation's message timeline, so it shows up in the chat on desktop and phones, like Messenger.

- When a call reaches a final state (ENDED, REJECTED, CANCELLED, MISSED), `CallService` calls `CallMessageService.recordFinishedCall`. That writes one message of type `CALL` with `call_id` set, the caller as sender, and the time the call finished. A group call is recorded once, when the whole call ends, not when one person leaves or declines.
- The message is published after commit on the normal `/user/queue/messages` queue, so open chats and the conversation list update in real time. It creates receipts for the other members like any message.
- `MessageResponse.call` (`MessageCallResponse`) carries `callType`, `status`, `callerId`, start/answer/end times, `durationSeconds` (null if nobody answered), `groupCall` and `joinedCount`. The client words it per viewer: the caller sees "Voice call · No answer", the other person sees a red "Missed voice call", and so on. Each entry has "Call back" / "Call again", which starts a new call of the same type.
- Clients cannot send `CALL` or `SYSTEM` messages; `/app/chat.send` turns those types into TEXT.

## Catching up after being offline, and joining later

Realtime call events reach only connected clients, so the client also asks the server what it missed.

- `GET /api/v1/calls/active` returns `ringing` (calls still ringing for me, within the ring timeout) and `joinable` (ACCEPTED calls in my conversations that I am not in, with room left). The client calls it on every WebSocket (re)connect, when the tab becomes visible, after call roster events, and when a CALL message arrives.
  - A `ringing` call opens the normal incoming-call screen, so someone who opens the app while being rung sees the call.
  - A `joinable` call shows "Call in progress · tap to join" in the chat list, and a Join banner in that chat.
- `POST /api/v1/calls/{id}/join` puts a conversation member into a running call. They may have missed it, declined it, left it, or not been rung (for example, busy at the time). Someone still being rung simply accepts. It needs an ACCEPTED call, an active membership, not being busy, and room under the participant limit. Everyone in the call gets `PARTICIPANT_JOINED`.
- `CallProvider` lives in `AppLayout`, above the routes. An incoming call rings on every page, and a call in progress survives moving between Chat, Users, Calls and Settings. The call panel is portalled into the main pane.
- While the app is in the background, an incoming call also shows a system notification, if the user turned on call alerts in Settings. This needs the page to be open (a background tab or app). Alerting a user who has closed the app would need Web Push (service worker, VAPID keys and a server push library), which is not part of the approved stack.
