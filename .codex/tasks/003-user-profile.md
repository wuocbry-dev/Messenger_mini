# Task

## Objective

Implement the initial user/profile management slice for Messenger Mini after authentication is available.

## Context

The approved schema already includes the `users` table with profile-related fields:

- `username`
- `email`
- `display_name`
- `avatar_object_key`
- `status`
- `last_seen_at`

Authentication/JWT should exist before this task is implemented because profile endpoints require an authenticated user.

## Functional Requirements

- Get the authenticated user's profile.
- Update the authenticated user's display name.
- Prepare avatar metadata handling using `avatar_object_key`.
- Search or lookup users for starting conversations, if explicitly approved in implementation scope.
- Do not expose password hashes.
- Use request/response DTOs.
- Keep code inside feature-based packages.

## API Contract

Use `/api/v1/...` paths.

Initial endpoints:

```text
GET   /api/v1/users/me
PATCH /api/v1/users/me/profile
```

Potential future endpoint, only if approved:

```text
GET /api/v1/users/search
```

Suggested DTOs:

```text
UserResponse
UpdateProfileRequest
```

Exact DTO fields should be confirmed before implementation if not already established by Auth.

## Business Rules

- A user may view their own profile.
- A user may update their own display name.
- `username` and `email` are not changed in the initial profile task unless explicitly requested.
- `password_hash` must never be returned.
- `avatar_object_key` may be returned only as safe metadata; temporary download URLs should not be persisted.
- User status should be respected by future business logic.

## Database Impact

Use the existing `users` table.

Do not change schema unless a mismatch is found and a `V2__...` migration is explicitly needed.

Future schema changes must start from `V2__...`.

## Realtime Impact

None for the initial profile update.

Presence-related profile fields such as `last_seen_at` should remain aligned with the realtime presence design.

## Security Requirements

- Require authentication.
- A user must not update another user's profile.
- Do not expose secrets or password hashes.
- Validate request DTOs.
- Keep Cloudflare R2 credentials backend-only.

## Error Cases

- Unauthenticated request.
- User not found.
- Inactive/deleted user.
- Invalid display name.
- Attempt to update fields outside the allowed scope.

## Tests

Add focused tests for:

- Authenticated user can fetch their own profile.
- Unauthenticated request is rejected.
- Authenticated user can update display name.
- Password hash is never returned.
- Invalid profile update is rejected.

Run:

```bash
cd backend
.\mvnw.cmd clean verify
```

## Acceptance Criteria

- Profile endpoints compile and are documented by OpenAPI.
- Endpoints use DTOs, not entities.
- Password hash is never returned.
- Authenticated ownership rules are enforced.
- Maven verification passes.
- Existing database data is preserved.

## Out of Scope

- Authentication implementation itself.
- Password change.
- Email change.
- Username change.
- Avatar binary upload to R2.
- Conversation/chat implementation.
- Presence/WebSocket implementation.
- Admin user management.
