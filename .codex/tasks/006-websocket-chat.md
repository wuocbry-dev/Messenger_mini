Implement real-time 1-to-1 chat using Spring WebSocket + STOMP.

Requirements:

- authenticate WebSocket users using the existing JWT authentication
- do not trust senderId supplied by client
- derive sender from authenticated principal
- verify sender belongs to conversation
- persist message before publishing realtime result
- publish MessageResponse DTO, not entity

Define and document STOMP contract.

Suggested directions:

client SEND:
 /app/chat.send

user-specific delivery:
 /user/queue/messages

or a conversation topic if authorization is enforced correctly.

Choose one design and document it in docs/REALTIME.md.

Message send request should contain only client-controlled fields such as:

conversationId
type
content

Server controls:

id
senderId
createdAt
delivery metadata

Support TEXT first.
Keep IMAGE/FILE compatible for later R2 integration.

Add integration tests where feasible.

Run clean verify.

Do not implement typing/presence/read receipts yet.