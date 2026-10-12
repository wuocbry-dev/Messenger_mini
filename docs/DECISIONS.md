# Architecture Decision Log

## ADR-001 - MySQL instead of PostgreSQL
Status: Accepted

Decision:
Use MySQL 8.x as the primary relational database.

## ADR-002 - Feature-based modular monolith
Status: Accepted

Decision:
Use a modular monolith organized by business feature.

## ADR-003 - Cloudflare R2 for object storage
Status: Accepted

Decision:
Use Cloudflare R2 for uploaded avatars, images, attachments, and files.

## ADR-004 - Agora for voice media
Status: Accepted

Decision:
Use Agora RTC for realtime voice media transport.

## ADR-005 - Vite + React + TypeScript frontend
Status: Accepted

Decision:
Use Vite, React, and TypeScript for the frontend.

## ADR-006 - Spring Boot backend
Status: Accepted

Decision:
Use Spring Boot for the backend API, realtime application events, security, persistence, and integration boundaries.

## ADR-007 - Flyway owns database schema
Status: Accepted

Decision:
Use Flyway migrations for schema evolution.
