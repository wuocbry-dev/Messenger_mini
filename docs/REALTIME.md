# Realtime

Messenger Mini uses WebSocket + STOMP for realtime application events.

## Realtime Responsibilities

WebSocket/STOMP should handle:

- Chat messages.
- Typing indicators.
- Delivered receipts.
- Read receipts.
- Presence updates.
- Call signaling where appropriate.

REST should primarily handle resource creation, lookup, and history queries. WebSocket should primarily handle events that users expect to see immediately.

## Boundaries

Agora carries voice media. WebSocket can coordinate call signaling and state changes, but Spring Boot must not proxy voice media streams.
