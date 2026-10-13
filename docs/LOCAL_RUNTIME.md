# Local Runtime

This guide starts the backend locally without committing secrets.

## Backend Environment

Create a local file from the example:

```powershell
Copy-Item backend\.env.example backend\.env
```

Then edit `backend/.env` with local values. This file is ignored by Git and must not be committed.

Safe placeholders are enough for unused integrations during basic backend startup. Real Cloudflare R2 and Agora secrets are not required until their features are implemented.

## Backend Commands

Use the Maven Wrapper from `backend/`. Do not require global Maven.

```powershell
cd backend
.\mvnw.cmd -v
.\mvnw.cmd clean verify
```

Load local environment variables and start Spring Boot. The bundled
`backend/run-be.ps1` does this for you and warns if any critical secret is
missing:

```powershell
.\run-be.ps1
```

If you prefer a manual one-liner instead of the script:

```powershell
Get-Content .env | ForEach-Object {
  if ($_ -match '^\s*([^#][^=]*)=(.*)$') {
    [Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim(), 'Process')
  }
}

.\mvnw.cmd spring-boot:run
```

## Runtime Checks

After startup:

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>

The UI is grouped by tag: `Auth`, `Users`, `Conversations`, `Messages`,
`Files`, `Calls`. Every protected operation shows a lock icon; click
**Authorize** at the top and paste a JWT access token (from
`POST /api/v1/auth/login`) to call them from the UI.

The base URL shown in the **Servers** dropdown comes from
`app.public-base-url` (env: `APP_PUBLIC_BASE_URL`,
default `http://localhost:8080`). Set this when running through a tunnel so
`Try it out` requests hit the right host.

## Database Safety

Normal application config must keep:

```yaml
spring:
  flyway:
    enabled: true
  jpa:
    hibernate:
      ddl-auto: validate
```

Do not enable permanent `baseline-on-migrate=true`.

The existing development database has already been baselined at Flyway version `1`. Future schema changes must start from `V2__...`.
