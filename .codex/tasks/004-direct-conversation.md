Implement 1-to-1 conversation management.

Required APIs:

POST /api/v1/conversations/direct
GET  /api/v1/conversations
GET  /api/v1/conversations/{conversationId}

POST body:

{
  "otherUserId": 123
}

Business rules:

- authenticated user becomes one member
- otherUserId must exist
- user cannot create a direct conversation with themselves
- DIRECT conversation must use deterministic direct_key:
  min(userAId,userBId) + ":" + max(userAId,userBId)
- repeated requests for the same pair return the existing conversation
- exactly the correct members are created
- authorization: only members may fetch conversation details
- use conversation_members
- do not create messages yet

Handle concurrent duplicate creation safely using DB uniqueness.

Return DTOs only.

Include last-message fields only if naturally supported; do not invent
message logic yet.

Add repository/service/controller tests.

Run clean verify.

Stop before Message implementation.