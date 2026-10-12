Implement the Messenger Mini frontend using the existing Vite + React +
TypeScript feature-based structure.

Do not redesign backend contracts.

Features:

1. Auth
   - register
   - login
   - logout
   - refresh/auth session
   - protected routes

2. User
   - current profile
   - user search/list

3. Chat
   - conversation list
   - direct conversation creation
   - message history
   - realtime messages
   - typing
   - delivered/read indicators
   - online/offline status

4. File
   - file/image upload
   - message attachments
   - download

5. Voice Call
   - incoming-call modal
   - call outgoing state
   - accept/reject
   - Agora Web SDK
   - microphone permission
   - join channel
   - publish audio
   - subscribe remote audio
   - mute/unmute
   - end call

Use:

features/auth
features/user
features/chat
features/file
features/call
shared/
app/

Do not put all API calls in one giant service.

Never expose backend-only secrets through VITE_*.

Handle loading/error/auth-expiration states.

Run:
npm build/test/lint commands available in the repository.

Report integration status feature by feature.