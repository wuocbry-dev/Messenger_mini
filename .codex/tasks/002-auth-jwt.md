# Task

## Objective

Implement the first authentication slice for Messenger Mini using Spring Security and JWT.

## Context

The backend foundation is ready:

- Maven Wrapper exists and is verified.
- Spring Boot starts locally.
- MySQL schema is under Flyway control.
- Existing schema is baselined at Flyway version `1`.
- Hibernate validates the approved schema.
- OpenAPI is available.

Authentication is an accepted project requirement, but no business auth flow has been implemented yet.

## Functional Requirements

- Implement register.
- Implement login.
- Implement access token generation.
- Implement refresh token flow.
- Implement logout/revoke refresh token.
- Add a current-user endpoint.
- Store users in the existing `users` table.
- Store refresh tokens in the existing `refresh_tokens` table.
- Hash passwords before storing them.
- Never return `password_hash`.
- Use request/response DTOs.
- Keep code inside feature-based packages.

## API Contract

Use `/api/v1/...` paths.

Initial endpoints:

```text
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
GET  /api/v1/users/me
```

Suggested request/response DTOs:

```text
RegisterRequest
LoginRequest
RefreshTokenRequest
AuthResponse
UserResponse
```

Exact DTO fields should be confirmed before implementation if not provided by the user.

## Business Rules

- `username` must be unique.
- `email` must be unique.
- `password_hash` must never be exposed.
- New users default to active status.
- Login should only succeed for active users.
- Refresh token values should be stored hashed, not plain text.
- Logout should revoke the refresh token.

## Database Impact

Use existing tables:

- `users`
- `refresh_tokens`

Do not change schema unless a mismatch is found and a `V2__...` migration is explicitly needed.

Future schema changes must start from `V2__...`.

## Realtime Impact

None for this task.

## Security Requirements

- Use Spring Security.
- Use JWT access tokens.
- Keep JWT secret backend-only.
- Use password hashing, preferably BCrypt.
- Do not log passwords, refresh tokens, JWTs, or secrets.
- Do not expose DB credentials, R2 secrets, or Agora App Certificate.
- Protect `/api/v1/users/me`.
- Keep Swagger/OpenAPI endpoints accessible for development.

## Error Cases

- Duplicate username.
- Duplicate email.
- Invalid login credentials.
- Inactive/blocked/deleted user login attempt.
- Invalid refresh token.
- Expired refresh token.
- Revoked refresh token.
- Missing bearer token for protected endpoint.

## Tests

Add focused tests for:

- Register success.
- Duplicate username/email.
- Password is hashed.
- Login success.
- Login rejects bad password.
- Refresh token success.
- Revoked refresh token rejection.
- `/api/v1/users/me` requires authentication.

Run:

```bash
cd backend
.\mvnw.cmd clean verify
```

If local database integration is used, verify data preservation expectations and do not delete existing development data.

## Acceptance Criteria

- Auth endpoints compile and are documented by OpenAPI.
- Passwords are stored hashed.
- JWT access token is returned on register/login/refresh.
- Refresh token is stored hashed.
- Protected current-user endpoint works with a valid JWT.
- No entity is returned directly from REST.
- No secrets are committed or printed.
- Maven verification passes.

## Out of Scope

- Frontend auth UI.
- Email verification.
- Password reset.
- OAuth/social login.
- Admin/user management screens.
- Chat/conversation/message implementation.
- Presence implementation.
- File/R2 upload implementation.
- Voice call implementation.
