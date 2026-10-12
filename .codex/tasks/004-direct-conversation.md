# Task

## Objective

Implement the initial 1-to-1 direct conversation slice for Messenger Mini.

## Context

The approved schema already includes:

- `conversations`
- `conversation_members`

Authentication must exist before this task is implemented because conversation operations require the current user.

The initial scope is direct conversations only. Group conversations are supported by schema shape but are not part of this task unless explicitly approved.

## Functional Requirements

- Create or get an existing direct conversation between the authenticated user and another user.
- List conversations for the authenticated user.
- Get conversation details for the authenticated user.
- Ensure direct conversations are unique per user pair.
- Use request/response DTOs.
- Keep code inside feature-based packages.

## API Contract

Use `/api/v1/...` paths.

Initial endpoints:

```text
POST /api/v1/conversations/direct
GET  /api/v1/conversations
GET  /api/v1/conversations/{conversationId}
```

Suggested DTOs:

```text
CreateDirectConversationRequest
ConversationResponse
ConversationMemberResponse
```

Exact DTO fields should be confirmed before implementation if not already established.

## Business Rules

- Only authenticated users can create/list/read conversations.
- Direct conversations must have exactly two active members.
- Direct conversations should use a deterministic `direct_key` so the same user pair maps to one conversation.
- A user cannot create a direct conversation with themselves.
- A user can only read conversations they are a member of.
- Repeated direct conversation creation for the same pair should return the existing conversation instead of creating a duplicate.

## Database Impact

Use existing tables:

- `conversations`
- `conversation_members`
- `users`

Do not change schema unless a mismatch is found and a `V2__...` migration is explicitly needed.

Future schema changes must start from `V2__...`.

## Realtime Impact

None required for the initial REST conversation slice.

Future realtime updates may notify users when conversations are created or updated.

## Security Requirements

- Require authentication.
- Enforce membership before returning conversation details.
- Do not expose password hashes or secrets.
- Validate request DTOs.

## Error Cases

- Unauthenticated request.
- Target user not found.
- Target user inactive/deleted.
- Attempt to create a conversation with self.
- Conversation not found.
- Authenticated user is not a member of the requested conversation.

## Tests

Add focused tests for:

- Create direct conversation success.
- Creating the same direct conversation twice returns the existing conversation.
- Cannot create direct conversation with self.
- Cannot create direct conversation with missing/inactive user.
- List conversations returns only conversations for the authenticated user.
- Get conversation rejects non-members.

Run:

```bash
cd backend
.\mvnw.cmd clean verify
```

## Acceptance Criteria

- Direct conversation endpoints compile and are documented by OpenAPI.
- Direct conversation uniqueness is enforced.
- Membership checks are enforced.
- Endpoints use DTOs, not entities.
- Maven verification passes.
- Existing database data is preserved.

## Out of Scope

- Group conversations.
- Message sending.
- Realtime WebSocket events.
- Typing indicators.
- Delivered/read receipts.
- File attachments.
- Voice calls.
- Frontend conversation UI.
