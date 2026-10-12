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
