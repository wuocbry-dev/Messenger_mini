# Messenger Mini

Messenger Mini is a university project for a web-based realtime messenger. The repository is currently initialized as a durable foundation for future implementation work.

## Current Status

Initialized:
- Repository documentation and Codex task workflow.
- Spring Boot backend scaffold.
- Vite React TypeScript frontend scaffold.
- MySQL/Flyway configuration boundaries.
- WebSocket/STOMP, Cloudflare R2, Agora, and OpenAPI documentation boundaries.

Planned, not implemented yet:
- Authentication and JWT flows.
- User/profile APIs.
- 1-to-1 conversations and realtime chat.
- Presence, typing indicators, delivered/read receipts.
- File/image upload flows using Cloudflare R2.
- 1-to-1 voice calling using Agora RTC.
- Call history APIs.

## Stack

- Backend: Java, Spring Boot, Maven, Spring Web, Spring Data JPA/Hibernate, Spring Security, JWT, WebSocket/STOMP, Flyway, Bean Validation, springdoc-openapi.
- Database: MySQL 8.x.
- Frontend: Vite, React, TypeScript.
- Object storage: Cloudflare R2.
- Voice media: Agora RTC.

## Architecture Summary

The project uses a client-server architecture with a modular monolith backend. Code is organized by feature first, with layered structure inside each feature when needed.

Backend package root:

```text
com.team503.messenger
```

Frontend code is split between app-level wiring, feature modules, and shared utilities.

## Repository Layout

```text
.
|-- AGENTS.md
|-- README.md
|-- .env.example
|-- .gitignore
|-- docker-compose.yml
|-- messenger_mini_mysql_schema.sql
|-- .codex/
|-- docs/
|-- backend/
`-- frontend/
```

## Backend and Frontend Relationship

The frontend calls REST APIs under `/api/v1/...` for resource/query operations and uses WebSocket/STOMP for realtime events. Agora RTC is used by the frontend for realtime voice media, while the backend owns call authorization, channel naming, token generation, and call state/history.

## Database and Flyway

MySQL is the primary relational database. Flyway owns schema evolution, and Hibernate should validate schema compatibility instead of updating the schema automatically.

The existing `messenger_mini_mysql_schema.sql` file is preserved as the current approved database baseline. The same schema is also available as Flyway migration `V1__initial_schema.sql` without `CREATE DATABASE` or `USE` statements.

Existing non-empty databases are baselined at Flyway version 1 so `V1` is not rerun over existing data. Empty databases run `V1` normally. Future schema changes should be added as Flyway migrations under:

```text
backend/src/main/resources/db/migration/
```

## Cloudflare R2

Cloudflare R2 stores uploaded avatars, images, and files. MySQL stores metadata and object keys only. Backend configuration reads R2 credentials from environment variables.

## Agora

Agora RTC carries voice media. The backend must keep the Agora App Certificate private and generate temporary RTC tokens only when authorized.

## API Documentation

springdoc-openapi is configured for the backend. Swagger UI will be available once backend endpoints exist and the application is running.

## Local Development

Start local MySQL:

```bash
docker compose up -d mysql
```

Start backend:

```bash
cd backend
mvn spring-boot:run
```

Start frontend:

```bash
cd frontend
npm install
npm run dev
```

Use `.env.example`, `backend/.env.example`, and `frontend/.env.example` as references. Do not commit real `.env` files.

## Codex Workflow

- `AGENTS.md` is the stable project contract for future Codex sessions.
- `.codex/ACTIVE_TASK.md` is where a temporary task can be pasted, but Codex should only execute it when explicitly told to.
- `.codex/tasks/` stores larger future task specifications referenced by name.
- `docs/` stores stable project context and architectural decisions.
