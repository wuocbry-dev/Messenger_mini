# Project Context

## Project Name
Messenger Mini

## Purpose
Messenger Mini is a web-based realtime messenger application for a university project.

## User-Facing Scope
- Authentication.
- User/profile management.
- 1-to-1 conversations.
- Realtime chat.
- Online/offline presence.
- Typing indicators.
- Delivered/read receipts.
- File and image attachments.
- 1-to-1 voice calls.
- Call history.

## Technology Stack
- Backend: Java, Spring Boot, Maven, Spring Web, Spring Data JPA/Hibernate, Spring Security, JWT, WebSocket/STOMP, Bean Validation, Flyway, springdoc-openapi.
- Frontend: Vite, React, TypeScript.
- Database: MySQL 8.x.
- Object storage: Cloudflare R2.
- Voice media: Agora RTC.
- Local infrastructure: Docker Compose may be used for MySQL.

## Important Boundaries
- This repository is a modular monolith, not a microservice system.
- MySQL stores relational data and file metadata only.
- Uploaded file binaries are stored in Cloudflare R2.
- Agora handles realtime voice media transport.
- Spring Boot handles authorization, call state/history, channel naming, and Agora token generation.
- Secrets must never be exposed to the Vite frontend.
