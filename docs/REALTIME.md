# Realtime

Messenger Mini uses WebSocket + STOMP for realtime application events.

## Realtime Responsibilities

WebSocket/STOMP should handle:

- Chat messages.
    10|- Typing indicators.
- Delivered receipts.
- Read receipts.
- Presence updates.
- Call signaling where appropriate.

REST should primarily handle resource creation, lookup, and history queries. WebSocket should primarily handle events that users expect to see immediately.

## Boundaries

Agora carries voice media. WebSocket can coordinate call signaling and state changes, but Spring Boot must not proxy voice media streams.

---

## 1-to-1 Chat STOMP Contract (task 006)

The realtime 1-to-1 chat slice is implemented with WebSocket + STOMP. This slice covers **TEXT** messages only; IMAGE/FILE are intentionally rejected in this task and will be wired up once the R2 file storage slice lands.

### Transport

- **Endpoint**: `ws://<host>/ws` (native WebSocket, no SockJS fallback).
- **STOMP broker prefixes** (configured in `WebSocketConfig`):
    - Application destination prefix: `/app`
    - User destination prefix: `/user`
    - Simple broker destinations: `/queue`, `/topic`
- **Authentication**: the client **MUST** present a JWT access token in the `Authorization: Bearer <token>` header of the **STOMP CONNECT** frame. The `StompAuthInterceptor` validates the token, looks up the user, and rejects the connection if the user is missing or inactive.
- **Identity**: server controls `senderId`. The client payload never carries a `senderId`; the authenticated principal is the only trusted source.

### Wire contract

| Direction | Destination | Payload | Notes |
|---|---|---|---|
| C → S | `/app/chat.send` | `SendChatMessageRequest` JSON | Server validates membership, persists, then publishes. |
| S → C | `/user/{username}/queue/messages` | `MessageResponse` JSON | Published once per active conversation member (including the sender for UI confirmation). |

#### Client send payload (`SendChatMessageRequest`)

```json
{
  "conversationId": 12,
  "type": "TEXT",
  "content": "Hello Alice"
}
```

- `type` is uppercased and parsed against `Message.Type`. Only `TEXT` is accepted in this task. Any other value triggers `UNSUPPORTED_MESSAGE_TYPE`.
- `content` is trimmed; empty / blank content triggers `EMPTY_MESSAGE`.
- `senderId` is **not** in the payload and is **never** trusted from the client.

#### Server publish payload (`MessageResponse`)

The server publishes the same DTO used by the REST `GET /api/v1/messages/{id}` endpoint so the frontend can render messages received via REST or STOMP identically.

```json
{
  "id": 987,
  "conversationId": 12,
  "senderId": 1,
  "senderUsername": "bob",
  "senderDisplayName": "Bob",
  "type": "TEXT",
  "content": "Hello Alice",
  "createdAt": "2026-10-12T12:34:56.789",
  "receipts": []
}
```

### Authorization rules

The STOMP `chat.send` handler enforces the same rules as the REST `POST /api/v1/conversations/{id}/messages` endpoint:

- The authenticated principal must be an active member of `conversationId` (checked via `ConversationMemberRepository.existsActiveMember`). Non-members trigger `NOT_A_MEMBER` and no realtime event is published.
- The same conversation, member, and message persistence rules are reused via `MessageService.sendChatMessage(...)`. Persistence happens **before** publishing, so a published payload always reflects a successfully stored message.

### Error handling

Errors are thrown as exceptions in the controller method. Spring's STOMP machinery converts them into `ERROR` frames on the same session; the controller does not currently publish custom error payloads. Frontends should treat unexpected frames as terminal for the in-flight send.

### Out of scope for task 006

The following are explicitly **not** covered here and belong to later tasks:

- Typing indicators.
- Delivered/read receipt realtime updates (REST writes receipts; real-time propagation is a later task).
- Presence updates.
- Call signaling.
- IMAGE/FILE messages (the contract is structured to support them but the backend rejects them until R2 storage is wired up).

---

## Chat State (task 007)

The chat state slice adds presence, typing, and receipt realtime propagation. The existing 1-to-1 chat wire contract is unchanged.

### Presence

Presence is tracked in-memory by `PresenceService`, which keeps a `userId -> Set<sessionId>` map. A user is **online** while at least one WebSocket session is registered; going offline is published only when the **last** session disconnects.

| Direction | Destination | Payload | Notes |
|---|---|---|---|
| Internal trigger | `SessionConnectedEvent` / `SessionDisconnectEvent` | n/a | `StompSessionPresenceListener` bridges events to `PresenceService`. |
| S → C | `/user/{username}/queue/presence` | `PresenceUpdate` JSON | Published on the user's own queue so cross-device sync works. |

```json
{
  "userId": 1,
  "online": true,
  "lastSeenAt": "2026-10-12T12:34:56.789"
}
```

`users.last_seen_at` is persisted with a **60-second debounce**. Heartbeats within the debounce window do not write to MySQL. A write is forced on the last session disconnect.

### Typing

Typing is **realtime only** — never persisted to MySQL. The principal sets `senderId`; clients cannot impersonate other users.

| Direction | Destination | Payload | Notes |
|---|---|---|---|
| C → S | `/app/typing` | `TypingRequest` JSON | `{ "conversationId": 1, "typing": true }` |
| S → C | `/user/{username}/queue/typing` | `TypingIndicator` JSON | Sent only to the *other* active members. A shared `/topic/...` was dropped because STOMP SUBSCRIBE is not membership-checked, so any client could have listened to any conversation. |

```json
{
  "conversationId": 1,
  "senderId": 1,
  "senderUsername": "bob",
  "typing": true,
  "emittedAt": "2026-10-12T12:34:56.789"
}
```

### Delivered / Read receipts

Receipts are persisted to `message_receipts` and realtime-propagated to the conversation. Server controls timestamps.

| Direction | Destination | Payload | Notes |
|---|---|---|---|
| C → S | `/app/receipts.mark` | `MarkReceiptRequest` JSON | `{ "messageId": 10, "state": "DELIVERED" }` (or `READ`) |
| S → C | `/user/{username}/queue/receipts` | `MessageReceiptUpdate` JSON | Published once per active member of the conversation. |

```json
{
  "messageId": 10,
  "conversationId": 1,
  "userId": 2,
  "state": "DELIVERED",
  "deliveredAt": "2026-10-12T12:34:56.789",
  "readAt": null
}
```

**Authorization rules for `receipts.mark`:**
- The recipient is taken from the authenticated principal — clients cannot mark messages on behalf of other users.
- The user must be an active member of the conversation.
- The sender cannot mark their own message as delivered or read (returns `CANNOT_RECEIPT_OWN_MESSAGE`).
- `MessageReceipt.markDelivered` and `markRead` are **idempotent** — the first timestamp wins.

### STOMP destination summary (tasks 006 + 007 + 009)

| Direction | Destination | Notes |
|---|---|---|
| C → S | `/app/chat.send` | Send a TEXT message. |
| C → S | `/app/typing` | Typing indicator. |
| C → S | `/app/receipts.mark` | Mark delivered/read. |
| S → C | `/user/{username}/queue/messages` | New messages addressed to the user. |
| S → C | `/user/{username}/queue/presence` | Presence updates for the user (cross-device). |
| S → C | `/user/{username}/queue/receipts` | Receipt updates addressed to the user. |
| S → C | `/user/{username}/queue/calls` | Call state transitions addressed to the user. |
| S → C | `/user/{username}/queue/typing` | Typing indicator from the other member. |

`/user/{username}/queue/...` is the server-side form used by `convertAndSendToUser`. **Clients subscribe without the username**, e.g. `/user/queue/messages`; Spring maps that to the authenticated session's private queue. Subscribing to `/user/<name>/queue/...` does not receive anything.
