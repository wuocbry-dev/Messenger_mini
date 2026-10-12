# Architecture

Messenger Mini uses a client-server architecture with a modular monolith backend.

## Backend Organization

Backend code is organized by business feature under:

```text
backend/src/main/java/com/team503/messenger/
```

Features include:

```text
auth/
user/
conversation/
message/
presence/
file/
call/
notification/
```

Layers exist inside each feature only when useful:

```text
feature/
|-- controller/
|-- service/
|-- repository/
|-- entity/
|-- dto/
|-- mapper/
```

## Dependency Flow

```text
Controller / WebSocket Handler
            |
            v
          Service
            |
            v
        Repository
            |
            v
          Entity
            |
            v
           MySQL
```

Request DTO flow:

```text
Client -> Controller -> Service
```

Response DTO flow:

```text
Service -> Mapper -> Response DTO -> Client
```

Entities must not be exposed directly as public API DTOs.

## Frontend Organization

Frontend code uses app, feature, and shared boundaries:

```text
frontend/src/
|-- app/
|-- features/
`-- shared/
```

Feature-specific API clients, hooks, types, and components should live inside the owning feature.
