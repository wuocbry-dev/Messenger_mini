# Task

## Objective

Implement the initial REST message slice for Messenger Mini.

## Context

The approved schema already includes:

- `messages`
- `message_receipts`
- `conversations`
- `conversation_members`

Authentication and direct conversations should exist before this task is implemented.

This task covers REST-based message creation and history retrieval only. Realtime WebSocket delivery is a separate future task.

## Functional Requirements

- Send a text message to a conversation.
- List messages in a conversation with pagination.
- Get a single message by ID if the authenticated user can access it.
- Create initial message receipt rows where appropriate.
- Use request/response DTOs.
- Keep code inside feature-based packages.

## API Contract

Use `/api/v1/...` paths.

Initial endpoints:

```text
POST /api/v1/conversations/{conversationId}/messages
GET  /api/v1/conversations/{conversationId}/messages
GET  /api/v1/messages/{messageId}
```

Suggested DTOs:

```text
SendMessageRequest
MessageResponse
MessagePageResponse
MessageReceiptResponse
```

Exact DTO fields should be confirmed before implementation if not already established.

## Business Rules

- Only authenticated users can send/read messages.
- A user can only send messages to conversations they are an active member of.
- A user can only read messages from conversations they are a member of.
- Initial message type for this task is `TEXT`.
- Empty text messages are not allowed.
- Message content should be trimmed or validated consistently.
- Do not implement file/image attachments in this task.
- Do not implement WebSocket broadcast in this task.

## Database Impact

Use existing tables:

- `messages`
- `message_receipts`
- `conversations`
- `conversation_members`
- `users`

Do not change schema unless a mismatch is found and a `V2__...` migration is explicitly needed.

Future schema changes must start from `V2__...`.

## Realtime Impact

None in this task.

Realtime message delivery, typing indicators, and receipt updates belong to later WebSocket tasks.

## Security Requirements

- Require authentication.
- Enforce conversation membership.
- Do not expose password hashes or secrets.
- Validate request DTOs.
- Do not return entities directly from REST.

## Error Cases

- Unauthenticated request.
- Conversation not found.
- Authenticated user is not a conversation member.
- Empty/invalid message content.
- Message not found.
- Message exists but is not visible to authenticated user.
- Unsupported message type.

## Tests

Add focused tests for:

- Authenticated member can send text message.
- Non-member cannot send message.
- Empty message is rejected.
- Member can list messages with pagination.
- Non-member cannot list messages.
- Member can get message by ID.
- Non-member cannot get message by ID.

Run:

```bash
cd backend
.\mvnw.cmd clean verify
```

## Acceptance Criteria

- Message REST endpoints compile and are documented by OpenAPI.
- Membership checks are enforced.
- Text message creation persists to `messages`.
- Message history is paginated.
- Endpoints use DTOs, not entities.
- Maven verification passes.
- Existing database data is preserved.

## Out of Scope

- WebSocket/STOMP realtime messaging.
- Typing indicators.
- Delivered/read receipt realtime updates.
- File/image attachments.
- Message editing/deletion.
- Search.
- Frontend chat UI.
