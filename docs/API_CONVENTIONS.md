# API Conventions

## Base Path

REST endpoints should use:

```text
/api/v1/...
```

## DTOs

- Use request DTOs for incoming payloads.
- Use response DTOs for outgoing payloads.
- Do not expose JPA entities directly.
- Never return password hashes or secret material.

## HTTP Status

- `200 OK` for successful reads and updates.
- `201 Created` for successful creation.
- `204 No Content` for successful delete or empty actions.
- `400 Bad Request` for validation failures.
- `401 Unauthorized` when authentication is missing or invalid.
- `403 Forbidden` when the authenticated user lacks permission.
- `404 Not Found` when a resource does not exist or is not visible to the user.

## Validation

Use Bean Validation annotations on request DTOs. Controllers should reject invalid input before business logic runs.

## Pagination

List endpoints should use explicit pagination parameters such as `page`, `size`, and sorting when needed.

## Error Response

A simple error response may include:

```json
{
  "timestamp": "2026-10-12T10:00:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "path": "/api/v1/example"
}
```

## OpenAPI

springdoc-openapi must document REST endpoints as they are implemented.

## Authentication

Initial authentication endpoints:

```text
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
GET  /api/v1/users/me
GET  /api/v1/users/search?q=<query>
PATCH /api/v1/users/me/profile
```

Register accepts `username`, `email`, `password`, and `displayName`. Login accepts `usernameOrEmail` and `password`. Refresh and logout accept `refreshToken`. User search returns lightweight results (id, username, displayName, avatarObjectKey, avatarUrl) without email or status.

Access tokens are sent as:

```text
Authorization: Bearer <accessToken>
```

Token lifetimes default to 1 hour for access tokens (`JWT_ACCESS_TOKEN_TTL_SECONDS`) and 90 days for refresh tokens (`JWT_REFRESH_TOKEN_TTL_DAYS`). Each refresh rotates the refresh token and starts a new 90-day window, so an active user stays signed in. The frontend keeps both tokens in localStorage, renews the access token shortly before it expires (and on a 401), and signs out only when the refresh token is rejected. Refresh tokens are stored only as SHA-256 hashes in MySQL. Passwords are stored only as BCrypt hashes. API responses must not include `password_hash`, JWT secrets, refresh token hashes, or integration secrets.
