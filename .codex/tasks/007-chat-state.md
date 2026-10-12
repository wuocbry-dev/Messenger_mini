Implement Messenger Mini realtime chat state features:

- online/offline presence
- last seen
- typing indicator
- delivered receipt
- read receipt

Presence:

- realtime online state should be managed in memory/WebSocket session state
- persist users.last_seen_at when appropriate
- do not continuously write every heartbeat to MySQL
- handle multiple browser sessions for the same user correctly

Typing:

- realtime only
- do not persist typing events in MySQL
- verify user belongs to the conversation

Delivered/read:

use message_receipts.

Requirements:

- recipient may mark delivered
- recipient may mark read
- server verifies authorization
- timestamps are server-generated
- duplicate events are idempotent
- sender receives realtime receipt updates

Document STOMP events/destinations in docs/REALTIME.md.

Add tests.

Run clean verify.

Stop before R2.