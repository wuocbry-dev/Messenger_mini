# Task — Authentication + JWT

## Objective

Implement production-quality basic authentication for Messenger Mini.

Implement:

- Register
- Login
- JWT access token
- Refresh token
- Refresh token rotation
- Logout/revoke
- Current user endpoint

Read:

- AGENTS.md
- backend/AGENTS.md
- docs/ARCHITECTURE.md
- docs/DATABASE.md
- docs/API_CONVENTIONS.md
- docs/DECISIONS.md

Inspect the current source before coding.

==================================================
1. API
==================================================

Implement:

POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout

GET /api/v1/users/me

Public:

/api/v1/auth/register
/api/v1/auth/login
/api/v1/auth/refresh
Swagger/OpenAPI resources

Authenticated:

/api/v1/auth/logout
/api/v1/users/me

==================================================
2. FEATURE-BASED STRUCTURE
==================================================

Use approximately:

auth/
├── controller/
│   └── AuthController.java
├── service/
│   └── AuthService.java
├── repository/
│   └── RefreshTokenRepository.java
├── entity/
│   └── RefreshToken.java
├── dto/
│   ├── request/
│   │   ├── RegisterRequest.java
│   │   ├── LoginRequest.java
│   │   └── RefreshTokenRequest.java
│   └── response/
│       └── AuthResponse.java
└── mapper/

user/
├── controller/
│   └── UserController.java
├── repository/
│   └── UserRepository.java
├── entity/
│   └── User.java
├── dto/response/
│   └── UserResponse.java
└── mapper/

common/security/
├── JwtService.java
├── JwtAuthenticationFilter.java
└── AuthenticatedUser.java if useful

Do not create meaningless abstractions.

==================================================
3. REGISTER
==================================================

Register request:

- username
- email
- password
- displayName

Validation:

username:
- required
- reasonable length

email:
- required
- valid email format

password:
- required
- minimum secure development length

displayName:
- required
- reasonable length

Rules:

- username must be unique
- email must be unique
- password must NEVER be stored raw
- use BCrypt PasswordEncoder
- status should become ACTIVE
- server controls createdAt/updatedAt
- client cannot supply id/passwordHash/status/timestamps

Return a Response DTO, never User entity.

==================================================
4. LOGIN
==================================================

Allow login with the approved identifier strategy.

Prefer username for initial scope unless existing documentation says
otherwise.

Verify password using BCrypt.

On success issue:

- short-lived access JWT
- refresh token

Do not place password/passwordHash inside JWT.

JWT should contain minimal identity information such as user id and
username/subject.

==================================================
5. REFRESH TOKENS
==================================================

Use existing refresh_tokens table.

Security requirement:

- generate cryptographically secure random refresh tokens
- return the raw refresh token only to the client
- store only a SHA-256 hash in MySQL
- never store the raw refresh token
- set expiry
- implement rotation

On refresh:

1. hash received token
2. locate matching DB row
3. verify not expired
4. verify not revoked
5. revoke old refresh token
6. issue new access token
7. issue new refresh token
8. persist new refresh-token hash

==================================================
6. LOGOUT
==================================================

Logout revokes the supplied/current refresh token.

Access JWT can remain stateless and expire naturally.

Do not create an access-token blacklist for this project unless required.

==================================================
7. SPRING SECURITY
==================================================

Replace temporary HTTP Basic setup with stateless JWT authentication.

Use:

SessionCreationPolicy.STATELESS

Disable HTTP Basic.

Keep CSRF handling appropriate for token-based REST.

Install JwtAuthenticationFilter.

Do not authenticate based on user-supplied userId headers.

The authenticated user identity comes from validated JWT.

==================================================
8. /users/me
==================================================

GET /api/v1/users/me

Return current authenticated user:

- id
- username
- email
- displayName
- avatar info if available
- status
- lastSeenAt
- createdAt

Never return passwordHash.

==================================================
9. DTO / ENTITY RULE
==================================================

Entity != Request DTO != Response DTO.

Controllers must not return JPA entities.

Use mapping code or mapper class.

Do not add MapStruct unless already approved.

==================================================
10. ERROR HANDLING
==================================================

Implement consistent errors for at least:

- validation error
- duplicate username
- duplicate email
- invalid credentials
- invalid refresh token
- expired refresh token
- revoked refresh token
- unauthorized request
- user not found

Use the project's API error convention.

Add/update GlobalExceptionHandler if needed.

Do not leak internal stack traces/password information.

==================================================
11. JWT CONFIG
==================================================

JWT secret must come from:

JWT_SECRET

Never hard-code it.

Fail clearly in non-test runtime if a secure JWT secret is required but
missing.

Tests may provide test-specific secret configuration.

Choose reasonable configurable values for:

access token expiry
refresh token expiry

Document them.

==================================================
12. DATABASE
==================================================

Use the existing schema.

Do NOT edit V1.

If a schema change is genuinely necessary:

create V2__... migration

But avoid changing the approved schema unless required.

==================================================
13. OPENAPI
==================================================

Document endpoints through springdoc.

Add Bearer JWT security scheme.

Swagger UI should allow:

Authorize -> Bearer token

Do not expose refresh-token hashes.

==================================================
14. TESTS
==================================================

Add useful tests.

At minimum cover:

Register:
- success
- duplicate username
- duplicate email
- validation failure

Login:
- success
- wrong password
- unknown username

Refresh:
- valid
- revoked
- expired/invalid

Security:
- /users/me rejected without JWT
- /users/me works with valid JWT

Logout:
- refresh token becomes unusable

Use the existing test conventions.

Do not wipe the developer's real MySQL data during tests.

Prefer isolated test configuration.

==================================================
15. VERIFICATION
==================================================

Run:

mvnw.cmd clean verify

Start backend.

Verify APIs through HTTP/Swagger.

Do not begin Conversation or Chat implementation.

==================================================
16. ACCEPTANCE CRITERIA
==================================================

Complete when:

- Register works
- passwords are BCrypt hashed
- Login works
- access JWT works
- refresh rotation works
- raw refresh tokens are not stored
- Logout revokes refresh token
- /users/me works
- REST entities are not exposed
- Swagger JWT authorization works
- tests pass
- clean verify passes
- existing Flyway baseline remains valid

Report:

- files changed
- endpoints created
- security flow
- tests run
- Swagger verification
- DB changes/migrations, if any
- remaining issues

Stop after Auth.