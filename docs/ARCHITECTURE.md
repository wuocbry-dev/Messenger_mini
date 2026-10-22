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

### Responsive Layout (Desktop / Phone)

One React app serves both layouts; there is no separate mobile app and no duplicated realtime or call state.

- Breakpoint: the phone layout is `(max-width: 767px), (max-height: 500px) and (pointer: coarse)`, which covers narrow screens and phones held in landscape (`MOBILE_QUERY` / `useIsMobile` in `shared/hooks`; CSS `@media` blocks use the same list). Wider screens, tablets included, use the desktop two-card layout. Below 1320px wide, its nav tabs stack each icon above its label so they fit the 320px left card.
- Shell: on phones `AppLayout` hides the left card and shows `MobileTabBar` (Chat `/`, Users `/users`, Calls `/calls`, Settings `/settings`), built from the same `navItems` as `AppSidebar`. The tab bar is hidden while a chat is open, during a voice call, and while an input has focus (on-screen keyboard). The account card with sign-out appears at the top of Settings, because phones have no left card.
- Conversation list: on desktop `ChatPanel` portals `Sidebar` into the left card through `SidebarSlotContext`. On phones the slot is `null`, so `ChatPanel` renders the same `Sidebar` inline as the Chat tab's home screen. Only one copy ever exists, and `MessengerPage`, `CallProvider` and `RealtimeProvider` never remount when the layout switches.
- List/detail navigation: the open chat is mirrored in the URL as `/?conversation=<id>`. Phones push a history entry when a chat is opened from the list, so browser Back/Forward and the in-header Back button move between list and chat. Desktop only replaces the entry. Deep links select the conversation after the list loads, and unknown ids fall back to `/`. The Users page (`location.state.openConversationId`) and the call UI (`OPEN_CONVERSATION_EVENT`) still work, and on phones they land directly in the chat.
- Keyboard: the viewport meta uses `interactive-widget=resizes-content` and `viewport-fit=cover`. On phones the app height follows `visualViewport` (`--app-height`), so the composer stays above the iOS keyboard, and inputs use 16px text to avoid focus zoom.
- Phone page and call adaptations are CSS-first inside each feature's existing component, so there's no second mobile component tree. Specifically:
  - The Users profile and Calls details open as full-screen pages.
  - Shared media opens as a full-screen page over the chat.
  - Dialogs fit the visible viewport.
  - Voice calls use a compact control grid.
  - Video calls always fill the viewport, with a compact bar (Mute, Video, Flip camera, More, End) and secondary actions (speaker, share screen, add people, back to chat, fullscreen) in a More sheet. Screen share and fullscreen appear only when the browser supports them.
  - The tab bar is hidden while any call UI (including the minimized pill) is shown, because leaving the Chat route unmounts `CallProvider`.
- Attachments open through `shared/utils/openResolvedUrl`, which opens the tab inside the tap before the presigned URL resolves, so mobile popup blockers allow it. R2 object keys and presigned-URL handling are unchanged.
