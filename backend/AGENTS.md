# Backend Agent Notes

- Package root: `com.team503.messenger`.
- Keep the backend feature-based. Do not add project-wide controller/service/repository/entity packages.
- Use layers inside each feature when useful.
- Controllers and WebSocket handlers call services; services call repositories.
- Do not expose entities directly from REST controllers.
- Use Bean Validation on request DTOs.
- Keep `spring.jpa.hibernate.ddl-auto=validate`.
- Add Flyway migrations under `src/main/resources/db/migration/` for schema changes.
- Keep R2 and Agora secrets backend-only.
- Use the Maven Wrapper (`mvnw.cmd` on Windows) for local backend verification.
- Keep `backend/.env` local and ignored; do not commit real secrets.
