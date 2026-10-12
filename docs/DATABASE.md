# Database

Messenger Mini uses MySQL 8.x as the primary relational database.

## Approved Baseline Tables

The current approved schema baseline includes:

- `users`
- `refresh_tokens`
- `conversations`
- `conversation_members`
- `messages`
- `message_receipts`
- `stored_files`
- `message_attachments`
- `calls`

The existing root file `messenger_mini_mysql_schema.sql` records the current approved SQL baseline.

`backend/src/main/resources/db/migration/V1__initial_schema.sql` contains the same table baseline without `CREATE DATABASE` or `USE` statements. Existing non-empty databases should be baselined at version 1 so Flyway records the current schema without rerunning `V1`. Empty databases should run `V1` normally.

## Rules

- Flyway owns schema evolution.
- Existing non-empty schemas are baselined at version 1.
- Hibernate `ddl-auto` must not be `update`.
- Prefer `spring.jpa.hibernate.ddl-auto=validate`.
- Never manually modify a production schema instead of creating a migration.
- Files are not stored as blobs in MySQL.
- Cloudflare R2 stores binary objects.
- `stored_files` stores metadata and object keys.
- Presence is primarily realtime/in-memory/WebSocket state.
- `users.last_seen_at` persists last seen information.
- Agora tokens and the Agora App Certificate are never persisted in `calls`.

## Migration Location

```text
backend/src/main/resources/db/migration/
```
