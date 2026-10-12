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

Load local environment variables and start Spring Boot:

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

- `GET http://localhost:8080/v3/api-docs`
- `GET http://localhost:8080/swagger-ui.html`

Expected OpenAPI metadata:

```text
title: Messenger Mini API
version: v1
```

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
