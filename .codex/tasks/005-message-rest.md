Implement persistence and REST query support for chat messages.

Required API:

GET /api/v1/conversations/{conversationId}/messages

Use pagination.

Business rules:

- only conversation members can read messages
- newest/oldest ordering must be clearly defined
- return MessageResponse DTO
- support current message types:
  TEXT
  IMAGE
  FILE
  SYSTEM
- initial send-message implementation may be service-level preparation;
  realtime sending will be completed in the WebSocket task
- do not expose entities
- no R2 implementation yet
- no fake attachment URLs

Implement MessageRepository queries optimized for:

conversation_id + created_at + id

Use existing DB indexes.

Add tests for:
- authorization
- pagination
- ordering
- empty history

Run clean verify.

Stop before WebSocket realtime delivery.