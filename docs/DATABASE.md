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
- `call_participants`

The existing root file `messenger_mini_mysql_schema.sql` records the current approved SQL baseline.

`backend/src/main/resources/db/migration/V1__initial_schema.sql` contains the same table baseline without `CREATE DATABASE` or `USE` statements. Existing non-empty databases should be baselined at version 1 one time only so Flyway records the current schema without rerunning `V1`. Empty databases should run `V1` normally.

## Rules

- Flyway owns schema evolution.
- Existing non-empty schemas are baselined at version 1 with a one-time Flyway baseline procedure.
- Do not keep automatic baselining enabled permanently in normal application config.
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

## One-Time Baseline Procedure

Use this only for an existing non-empty development database that already contains the approved V1 schema and data.

1. Backup the database.
2. Verify the 9 approved tables exist.
3. Run a one-time Flyway baseline at version 1.
4. Confirm `flyway_schema_history` contains version `1` with type `BASELINE`.
5. Leave normal application config with Flyway enabled and Hibernate `ddl-auto=validate`.

Preferred Flyway CLI form:

```bash
flyway \
  -url="jdbc:mysql://<host>:3306/messenger_mini" \
  -user="<user>" \
  -password="<password>" \
  -baselineVersion=1 \
  -baselineDescription="Existing Messenger Mini schema" \
  baseline
```

If using Spring Boot for the one-time baseline, pass baseline settings as temporary runtime environment variables for that one run only, then remove them:

```bash
SPRING_FLYWAY_BASELINE_ON_MIGRATE=true
SPRING_FLYWAY_BASELINE_VERSION=1
SPRING_FLYWAY_BASELINE_DESCRIPTION="Existing Messenger Mini schema"
```

Do not commit these as permanent application settings.

Future schema changes must start from `V2__...`.
