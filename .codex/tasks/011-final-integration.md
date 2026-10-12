Perform final Messenger Mini integration and hardening.

Do not add new product scope.

Verify end-to-end:

Register
→ Login
→ Find user
→ Open direct conversation
→ Send realtime message
→ Typing
→ Delivered
→ Read
→ Upload file/image
→ Download attachment
→ Start voice call
→ Accept
→ Agora audio
→ End call
→ Call history
→ Logout

Review:

- authorization on every conversation/message/file/call operation
- input validation
- exception handling
- JWT security
- refresh-token handling
- WebSocket authentication
- WebSocket authorization
- upload size/type validation
- R2 secret handling
- Agora certificate handling
- SQL indexes
- N+1 queries
- transaction boundaries
- race conditions
- duplicate direct conversations
- duplicate receipts
- illegal call transitions

Run full backend tests.
Run frontend build/tests.
Run integration tests available.

Update:
README
docs/API_CONVENTIONS.md
docs/REALTIME.md
docs/STORAGE.md
docs/VOICE_CALL.md

Do not claim a test passed unless actually executed.

Produce a final demo checklist.