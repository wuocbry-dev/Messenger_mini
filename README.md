# Messenger Mini

Messenger Mini is a university project for a web-based realtime messenger with authentication, profiles, direct conversations, chat, presence, typing indicators, receipts, attachments, and 1-to-1 voice calls.

## Features (Implemented)

- **Authentication** — Register, login, JWT access tokens, refresh tokens, logout.
- **User profiles** — View and update display name and avatar.
- **Direct conversations** — Create, list, and open 1-to-1 conversations.
- **Realtime chat** — Send and receive TEXT messages via WebSocket/STOMP.
- **Typing indicators** — Realtime typing state broadcast to conversation participants.
- **Receipts** — Delivered and read receipt tracking and propagation.
- **Presence** — Online/offline status tracked per WebSocket session.
- **File upload** — Upload images and files to Cloudflare R2 with presigned URLs.
- **Voice calls** — Start, accept, reject, and end Agora RTC voice calls.
- **Call history** — Paginated list of past calls per user or per conversation.

## Stack

- Backend: Java 17, Spring Boot 3.3.5, Maven, Spring Data JPA, Spring Security, JWT (HS256), WebSocket/STOMP, Flyway, springdoc-openapi.
- Database: MySQL 8.x.
- Frontend: Vite, React 18, TypeScript.
- Object storage: Cloudflare R2.
- Voice media: Agora RTC SDK (Web SDK).

## Architecture

Client-server. Modular monolith backend (`com.team503.messenger`). Feature-based package structure (auth, user, conversation, message, presence, file, call). Frontend uses `frontend/src/features/` for feature code and `frontend/src/shared/` for cross-cutting utilities.

## Repository Layout

```text
.
|-- AGENTS.md                        # Project contract for AI agents
|-- README.md                         # This file
|-- .env.example                     # Root env vars
|-- docker-compose.yml               # MySQL for local dev
|-- .codex/tasks/                   # Feature task specs
|-- docs/                           # Architecture & API docs
|-- backend/                        # Spring Boot app
|   |-- src/main/java/com/team503/messenger/
|   |-- src/main/resources/db/migration/  # Flyway migrations
|-- frontend/                       # Vite + React + TypeScript
|   |-- src/app/                    # App layout & page routing
|   |-- src/features/              # Feature modules
|   |-- src/shared/                # UI primitives, API, hooks
|   |-- src/types/                  # TypeScript shared types
```

## Getting Started

### 1. Start MySQL

```bash
docker compose up -d mysql
```

### 2. Configure environment

Copy and configure environment files:

```bash
cp .env.example .env
cp backend/.env.example backend/.env
cp frontend/.env.example frontend/.env
```

See `backend/.env.example` and `frontend/.env.example` for all required variables.

### 3. Start backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

### 4. Start frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend runs at `http://localhost:5173`. Backend API at `http://localhost:8080`.

### 5. Access API docs

Swagger UI: `http://localhost:8080/swagger-ui/index.html`

## Key Design Notes

### Security

- JWT access tokens (HS256, short-lived) + refresh tokens (long-lived, revocable).
- `Authorization: Bearer <token>` on every REST request.
- STOMP CONNECT requires a valid JWT via `StompAuthInterceptor`.
- R2 secret keys and Agora App Certificate are backend-only.
- File downloads require authentication — no anonymous file enumeration.
- Typing and message sending require active conversation membership.

### WebSocket Topics

| Destination | Purpose |
|---|---|
| `/app/chat.send` | Client sends a message |
| `/app/typing` | Client sends typing indicator |
| `/app/receipts.mark` | Client marks message delivered/read |
| `/user/{u}/queue/messages` | Server → client: new message |
| `/user/{u}/queue/typing` | Server → client: typing event |
| `/user/{u}/queue/receipts` | Server → client: receipt update |
| `/user/{u}/queue/presence` | Server → client: presence change |
| `/user/{u}/queue/calls` | Server → client: call state transition |
| `/topic/conversations/{id}/typing` | Broadcast to conversation members |

### Database

Flyway owns all schema evolution. `backend/src/main/resources/db/migration/` holds migrations. Hibernate runs in `validate` mode.

### Agora Voice Calls

The backend owns authorization, call state, channel naming, and Agora token generation. The frontend Agora RTC SDK handles voice media transport directly with Agora's servers. Tokens are short-lived and never persisted.

## Codex Workflow

- `AGENTS.md` — stable project contract for AI agents.
- `.codex/tasks/` — feature task specifications.
- `docs/` — architecture decisions and API contracts.
