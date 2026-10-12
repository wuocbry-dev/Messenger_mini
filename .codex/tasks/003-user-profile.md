Implement the Messenger Mini User/Profile feature.

Read all applicable AGENTS.md and project docs first.

Required APIs:

GET /api/v1/users/me
PATCH /api/v1/users/me

GET /api/v1/users
GET /api/v1/users/{userId}

Requirements:

- authenticated APIs
- pagination for user list
- search by username/displayName where reasonable
- never expose passwordHash
- UserResponse DTO only
- user cannot arbitrarily change security-sensitive fields
- profile update may change displayName
- avatar integration is NOT implemented yet; only preserve existing
  avatarObjectKey support
- status administration is out of scope

Use:
Controller -> Service -> Repository

Add tests.

Run clean verify.

Do not begin conversations/chat.