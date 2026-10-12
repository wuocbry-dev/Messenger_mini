# Task

## Objective

Convert the existing `messenger_mini` MySQL schema into the first Flyway baseline migration and mark the existing live database as baselined without deleting or recreating data.

## Context

The live MySQL database already contains the approved Messenger Mini schema. Flyway was enabled, but `backend/src/main/resources/db/migration/` initially had no migration. Without a baseline, Flyway would not know how the existing schema was created.

## Functional Requirements

- Create `backend/src/main/resources/db/migration/V1__initial_schema.sql`.
- Include the approved initial tables:
  - `users`
  - `refresh_tokens`
  - `conversations`
  - `conversation_members`
  - `messages`
  - `message_receipts`
  - `stored_files`
  - `message_attachments`
  - `calls`
- Do not include `CREATE DATABASE`.
- Do not include `USE messenger_mini`.
- Baseline the existing non-empty schema at version 1 with a safe one-time procedure.
- Do not leave unsafe automatic baselining permanently enabled in normal application config.
- Keep Hibernate configured with `ddl-auto: validate`.
- Backup the live database before touching Flyway metadata.
- Do not commit local credentials or backup files.

## API Contract

No REST or WebSocket API changes.

## Business Rules

Existing database schema and data must be preserved. Flyway version `1` represents the already-approved initial schema.

## Database Impact

- Added migration file `V1__initial_schema.sql` for new empty databases.
- Existing live database was baselined by creating `flyway_schema_history` with version `1`.
- No application tables were dropped, recreated, or overwritten.

## Realtime Impact

None.

## Security Requirements

- Do not commit `backend/.env`.
- Do not commit DB password.
- Do not commit JWT secret.
- Do not commit R2 secret.
- Do not commit Agora App Certificate.
- Do not commit database backups.

## Error Cases

- If a database already has application tables but no Flyway history, baseline it instead of running `V1`.
- If a database is empty, Flyway should run `V1__initial_schema.sql` normally.
- If credentials are unavailable, stop before modifying Flyway metadata.

## Tests

- Verify `V1__initial_schema.sql` contains all 9 approved tables.
- Verify `V1__initial_schema.sql` does not contain `CREATE DATABASE` or `USE`.
- Verify `spring.flyway.enabled=true`.
- Verify the live database has `flyway_schema_history` version `1` with type `BASELINE`.
- Verify normal application config does not require permanent automatic baselining.
- Verify `spring.jpa.hibernate.ddl-auto=validate`.
- Verify ignored files are not staged.
- Scan staged files for committed secrets.

## Acceptance Criteria

- Backup SQL exists locally under ignored `backups/`.
- `V1__initial_schema.sql` exists and matches the approved schema baseline.
- Existing DB has `flyway_schema_history` with version `1` as baseline.
- Commit is created without local secrets or backup files.

## Out of Scope

- Implementing entities, repositories, services, controllers, or business features.
- Adding future migrations beyond `V1`.
- Changing the approved schema.
- Resetting or recreating the live database.
