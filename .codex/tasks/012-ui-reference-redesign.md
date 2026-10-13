# Task — Rebuild Messenger Mini Frontend From 9 UI Reference Images

## Objective

Redesign and complete the Messenger Mini frontend so that it matches the
9 reference screenshots stored in:

docs/ui/

as closely as practical.

IMPORTANT:

The reference images are the VISUAL SOURCE OF TRUTH.

Do not redesign them into your own style.
Do not invent a different dashboard.
Do not simplify them into a generic CRUD interface.

The goal is:

"Make the real Messenger Mini application visually look like these
reference screenshots while preserving all existing working backend,
REST, WebSocket, R2 and Agora functionality."

==================================================
1. READ PROJECT INSTRUCTIONS FIRST
==================================================

Before making any changes, read:

- /AGENTS.md
- /frontend/AGENTS.md
- /docs/PROJECT_CONTEXT.md
- /docs/ARCHITECTURE.md
- /docs/API_CONVENTIONS.md
- /docs/REALTIME.md
- /docs/STORAGE.md
- /docs/VOICE_CALL.md

Also inspect:

- completed backend API implementation
- existing frontend
- current routing
- current authentication
- current WebSocket implementation
- current R2/file integration
- current Agora implementation

Do not assume API contracts.
Inspect the actual implementation first.

==================================================
2. INSPECT ALL 9 REFERENCE IMAGES
==================================================

Enumerate every PNG image under:

docs/ui/*.png

There should currently be 9 reference images.

Do not rely on their filenames to understand them.

OPEN AND VISUALLY INSPECT every image.

Before coding, classify each screenshot by screen/state.

The set is expected to visually represent approximately:

1. Login
2. Registration
3. Main Messenger / Chat
4. User Directory
5. Profile / Settings
6. Chat with shared files/media
7. Call History
8. Incoming Voice Call
9. Active Voice Call

Determine the exact mapping by viewing the images.

Create a short internal UI inventory before implementation.

If image viewing is unavailable in the current environment:

STOP.

Do not guess the design from filenames.

Report that the image references could not be visually inspected.

==================================================
3. REFERENCE PRIORITY
==================================================

When visual decisions conflict with the current UI:

REFERENCE IMAGES WIN for visual design.

When a reference contains a product capability that the real backend
does not support:

REAL PRODUCT SCOPE WINS for functionality.

Example:

If a screenshot contains:
- Video Call
- Group Chat
- Premium plan

but the current Messenger Mini implementation only supports:
- 1-to-1 chat
- files/images
- 1-to-1 Voice Call

then:

DO NOT implement fake Video Call, Group Chat or Premium business logic.

Instead preserve the layout/style while removing or adapting unsupported
controls.

Do not show fake working buttons.

==================================================
4. GLOBAL VISUAL LANGUAGE
==================================================

Derive exact visual details from the 9 screenshots.

Use the screenshots to determine:

- spacing
- dimensions
- panel widths
- border radius
- shadows
- font sizes
- font weights
- icon size
- avatar size
- background colors
- surface colors
- active states
- hover states
- message bubble styling
- form styling
- call UI styling

The approximate design system visible in the references is:

Primary Indigo:
#5B5BD6

Strong Indigo:
#5548E8

Accent Violet:
#7C5CFC

Page Background:
#F5F7FC / #F7F8FC

Surface:
#FFFFFF

Soft Primary:
#F1F0FF

Border:
#E7E9F1

Primary Text:
#111827

Secondary Text:
#697386

Muted:
#98A2B3

Online:
#22C55E

Danger:
#EF4444

Use actual sampled/reference colors where practical.

Create reusable design tokens.

Avoid random one-off colors.

==================================================
5. TYPOGRAPHY
==================================================

Use a modern SaaS typography system consistent with the references.

Prefer Inter.

Suggested fallback:

Inter,
ui-sans-serif,
system-ui,
-apple-system,
BlinkMacSystemFont,
"Segoe UI",
sans-serif

Create consistent typography for:

- page headings
- sidebar labels
- conversation names
- usernames
- timestamps
- message text
- input labels
- helper text
- status labels
- call duration

Do not let each feature invent its own typography.

==================================================
6. APPLICATION SHELL
==================================================

Build a reusable application shell matching the reference screenshots.

Desktop layout should resemble:

┌─────────────────┬────────────────────┬─────────────────────────────┐
│ App Navigation  │ Feature/List Panel │ Main Content                │
│                 │                    │                             │
└─────────────────┴────────────────────┴─────────────────────────────┘

For Chat:

Navigation:
~250-280px

Conversation panel:
~340-380px

Chat:
minmax(0, 1fr)

The application must use the full available viewport.

Do NOT reproduce the current broken behavior where chat occupies only
a narrow area and most of the screen is blank.

Use:

CSS Grid / Flexbox

not absolute positioning for the primary layout.

Ensure:

min-width: 0

on flex/grid children where necessary.

==================================================
7. APP NAVIGATION SIDEBAR
==================================================

Match the reference.

Include:

Messenger Mini branding

Navigation:

- Chat
- Users
- Calls
- Settings

Bottom:

- authenticated user avatar
- actual username/display name
- logout/menu control if currently supported

Selected navigation item:

- soft indigo background
- indigo icon
- indigo text
- rounded corners

Use Lucide icons if appropriate.

Do not hard-code the fake users from screenshots.

Do not implement the Premium card unless it is purely decorative and
does not distract from the real project.

Prefer omitting Premium because it is not part of the project scope.

==================================================
8. LOGIN SCREEN
==================================================

Use the Login reference image.

Reproduce:

- split-screen layout
- Messenger Mini branding
- attractive left promotional/design section
- right login card/form
- large rounded white surfaces
- subtle purple/indigo background decorations
- professional input controls
- show/hide password icon
- primary Sign In button
- link to Register

DO NOT add fake Google OAuth if backend does not support Google login.

If the reference has a Google button:

omit or visually disable it unless Google OAuth is truly implemented.

Use real API:

POST /api/v1/auth/login

Preserve current JWT/refresh-token behavior.

==================================================
9. REGISTRATION SCREEN
==================================================

Use the registration screenshot.

Reproduce:

- matching branding
- split layout
- display name
- username
- email
- password
- confirm password if frontend validation supports it
- validation feedback
- strong primary CTA
- link back to Login

Use actual backend Register API.

Do not send fields the backend does not accept.

==================================================
10. MAIN CHAT SCREEN
==================================================

This is one of the highest priority screens.

Match the Chat reference extremely closely.

Desktop structure:

Navigation
    |
Conversation List
    |
Chat Panel

Conversation panel must contain:

- title "Conversations"
- plus/new conversation action
- search field
- filter controls if compatible
- avatar
- online state
- conversation name
- latest message
- timestamp
- unread indicator if available
- typing state
- selected state

Do not add Group functionality if Group Chat is not implemented.

==================================================
11. CHAT HEADER
==================================================

Match reference:

Left:

- user avatar
- display name
- Online / Last Seen

Right:

- Voice Call
- Search if supported
- More menu if useful

Do NOT display Video Call unless the app actually supports Video Call.

Use existing Voice Call behavior.

==================================================
12. MESSAGE LIST
==================================================

Use the full center content area.

Incoming:

left aligned

Outgoing:

right aligned

Reference style:

Incoming bubble:
light gray / blue-gray

Outgoing:
indigo / violet

Rounded bubble shapes.

Use readable widths.

Suggested:

max-width: min(65%, 640px)

Show:

- timestamp
- delivered/read indicator
- avatar where reference uses it
- typing indicator

Ensure long text wraps correctly.

Ensure messages do not overflow.

==================================================
13. MESSAGE COMPOSER
==================================================

Rebuild composer to match reference.

Full-width bottom composer.

Include:

- add/attach action
- message field
- emoji control if already supported or purely visual-safe
- attachment
- send button

Primary send button:

- circular
- indigo
- white send icon

Composer should feel integrated into the chat panel.

It must NOT remain narrow like the old UI.

==================================================
14. IMAGE / FILE MESSAGES
==================================================

Match the shared-media/reference screens.

Images:

- polished thumbnails
- rounded corners
- correct object-fit
- preview behavior if already implemented

Files:

- file icon
- filename
- file type
- size
- download action

Never show:

- Cloudflare R2 secret
- object storage internal key
- presigned URL text

Use application metadata only.

==================================================
15. USER DIRECTORY SCREEN
==================================================

Use the User Directory screenshot.

Reproduce:

- title
- search bar
- user cards/list
- avatar
- username
- display name
- online status
- Message button
- optional user details side panel

IMPORTANT:

Only render fields the backend really provides.

Do not fabricate:

- company
- city
- department
- skills
- role

unless those fields actually exist.

The reference image is for VISUAL STRUCTURE.

Real data comes from Messenger Mini backend.

"Message" should create/open a direct conversation.

==================================================
16. PROFILE / SETTINGS
==================================================

Use the Settings screenshot.

Create a polished Settings/Profile page.

Show actual supported fields such as:

- avatar
- displayName
- username
- email
- status if applicable

Use existing Profile API.

Do not invent unsupported backend settings.

Sections may visually resemble:

Profile
Account
Security
Appearance

but only functional settings should be interactive.

Unsupported sections can be omitted.

==================================================
17. SHARED MEDIA / FILES
==================================================

Use the media/files screenshot as reference.

When appropriate, implement a right-side conversation details/media
panel showing existing attachments.

Possible sections:

Media
Files

Only add Links if there is real link/message support available.

Use current R2/file APIs.

Match:

- media grid
- file rows
- download controls
- right drawer width
- tabs
- clean border hierarchy

==================================================
18. CALL HISTORY
==================================================

Use the Call History screenshot.

Create a Calls page based on actual backend:

GET call history API / existing contract

Display:

- participant
- avatar
- incoming/outgoing
- missed/rejected/ended state
- date/time
- duration if available

The project currently focuses on Voice Call.

Do NOT label calls as Video unless actual backend data supports VIDEO.

Use status color coding matching the screenshots.

Clicking/selecting a call may show detail pane if the existing data
supports it.

==================================================
19. INCOMING VOICE CALL
==================================================

Use the incoming-call screenshot.

Match:

- dimmed/blurred application background
- centered call modal
- avatar
- caller display name
- "Incoming voice call"
- Reject red button
- Accept green button
- subtle visual waveform/ringing decoration

Use actual incoming Agora/call state.

Do not fake incoming calls.

Ensure modal is responsive.

==================================================
20. ACTIVE VOICE CALL
==================================================

Use the active-call screenshot.

Create a polished call interface.

Display:

- participant avatar
- name
- call duration
- Voice Call label
- mute/unmute
- speaker/audio state where technically supported
- end call

Use real Agora integration.

Do not create fake Video button.

Match reference proportions and spacing.

End Call should be prominent red.

Mute state should be visually clear.

==================================================
21. AVATARS
==================================================

Reference images use polished avatars.

Real project rules:

If avatar exists:
use it.

If not:

generate fallback avatar from initials.

Example:

Nguyễn Văn Quốc
→ NQ or Q depending existing convention.

Use deterministic fallback colors.

Online indicator:

small green circle
bottom-right of avatar
white border

Do not load random external avatar services.

==================================================
22. LOADING STATES
==================================================

Create polished loading states matching the final design.

Use:

- skeleton conversation rows
- message skeletons where appropriate
- button loading state
- form loading state

Avoid raw:

"Loading..."

in the final polished UI where better presentation is practical.

==================================================
23. EMPTY STATES
==================================================

Add intentional empty states.

Examples:

No active conversation:

"Select a conversation"
"Choose someone to start messaging."

No messages:

"No messages yet"
"Send the first message."

No calls:

"No call history yet."

No users found:

"No users found."

Keep them consistent with reference visuals.

==================================================
24. ERROR STATES
==================================================

Do not render raw backend errors into the page.

Use:

- inline validation
- toast
- compact error banner

Examples:

Login failed
Message failed
Upload failed
Call failed

Preserve useful backend messages but present them professionally.

==================================================
25. RESPONSIVENESS
==================================================

Test the design at minimum:

1920 × 1080
1440 × 900
1366 × 768
1024 × 768

Desktop should closely match the references.

At smaller widths:

- collapse navigation
- allow conversation/chat transitions
- prevent horizontal overflow

Do not sacrifice desktop reference fidelity unnecessarily.

==================================================
26. REAL DATA ONLY
==================================================

This is critical.

Screenshots contain fake names such as:

Sarah Kim
Marcus Lee
Priya Shah
etc.

DO NOT hard-code them.

They are reference/demo data only.

The final UI must render the real data returned from:

Spring Boot
MySQL
WebSocket
R2
Agora

The references define:

STYLE
LAYOUT
SPACING
VISUAL STATES

not application data.

==================================================
27. PRESERVE WORKING FUNCTIONALITY
==================================================

Before refactoring, inspect all current frontend feature code.

Do NOT break:

- Login
- Register
- JWT refresh
- User/Profile
- User search
- Direct Conversation
- Message history
- realtime WebSocket messages
- online/offline
- typing
- delivered/read
- file upload
- file download
- R2
- incoming calls
- accept/reject
- Agora Voice Call
- call history
- logout

Do not rewrite working network logic merely because components are
being restyled.

==================================================
28. COMPONENT ARCHITECTURE
==================================================

Keep feature-based frontend structure.

Use reusable components where helpful.

Suggested shared components:

AppSidebar
Avatar
OnlineIndicator
IconButton
Button
Input
Modal
Drawer
Badge
EmptyState
Skeleton
Toast

Chat components:

ConversationPanel
ConversationItem
ChatHeader
MessageList
MessageBubble
AttachmentMessage
TypingIndicator
MessageComposer
SharedMediaDrawer

Call components:

IncomingCallModal
ActiveCallView
CallHistoryList
CallHistoryItem

Do not make one giant component.

==================================================
29. CSS / DESIGN TOKENS
==================================================

Create a coherent theme.

Do not scatter:

#5B5BD6

across 50 files manually.

Use CSS custom properties or the existing theme mechanism.

Example:

:root {
  --primary: #5B5BD6;
  --primary-strong: #5548E8;
  --accent: #7C5CFC;
  --background: #F7F8FC;
  --surface: #FFFFFF;
  --border: #E7E9F1;
  --text: #111827;
  --text-secondary: #697386;
  --muted: #98A2B3;
  --success: #22C55E;
  --danger: #EF4444;
}

Also centralize:

- border radius
- spacing
- shadows
- transition speeds

==================================================
30. ICONS
==================================================

Use a consistent icon set.

Prefer:

Lucide React

if already available or if a small dependency addition is justified.

Do not mix:

emoji
random SVG
Font Awesome
Lucide
browser icons

throughout the UI.

==================================================
31. VISUAL COMPARISON LOOP
==================================================

This section is mandatory.

After implementing each major screen:

1. Run the frontend.
2. Render/open the real page.
3. Compare it with its corresponding reference image.
4. Identify visual differences.
5. Adjust CSS/layout.
6. Repeat.

Do not stop after:

"npm run build passed."

A successful compile does NOT mean the UI matches the references.

Compare:

- panel width
- page margins
- alignment
- font size
- typography weight
- bubble shape
- color
- spacing
- icon size
- avatar size
- border
- shadow
- button shape
- header height
- composer height
- modal positioning

Aim for high visual fidelity.

==================================================
32. DO NOT BLINDLY COPY UNSUPPORTED FEATURES
==================================================

Reference screenshots may visually contain product concepts not in the
current backend.

Do not expand project scope just to match them.

Examples:

Premium:
omit.

Group chat:
omit unless implemented.

Video Call:
omit unless implemented.

Google Login:
omit unless implemented.

Skills/Department/Location:
omit unless backend has them.

Keep visual balance after removing unsupported controls.

==================================================
33. BUILD / VERIFICATION
==================================================

After redesign:

Run available frontend checks.

At minimum:

npm run build

Also run if configured:

npm run lint
npm run typecheck
npm test

Fix errors introduced by UI refactoring.

Also perform functional smoke testing.

==================================================
34. REQUIRED MANUAL/SMOKE FLOWS
==================================================

Verify:

Flow 1:
Register
→ Login
→ Messenger

Flow 2:
Find another user
→ Start conversation

Flow 3:
Open conversation
→ History displays
→ Send message
→ realtime message appears

Flow 4:
Typing
→ delivered
→ read

Flow 5:
Upload file/image
→ attachment message
→ download

Flow 6:
Start Voice Call
→ other user receives Incoming Call
→ Accept
→ Agora audio
→ Mute
→ End

Flow 7:
Call History

Flow 8:
Profile update

Do not claim successful flows if they were not actually executed.

==================================================
35. FINAL REPORT
==================================================

At completion return:

### Reference Analysis
List all 9 images and which screen/state each represents.

### Screens Implemented
- Login
- Register
- Chat
- Users
- Settings/Profile
- Shared Media/Files
- Call History
- Incoming Call
- Active Call

### Visual Fidelity
Explain major design choices matched to reference.

### Components Created/Refactored

### Existing Functionality Preserved

### Unsupported Reference Features Omitted

### Responsive Verification

### Commands Executed

### Build/Test Results

### Remaining Visual Differences

### Remaining Functional Blockers

Do NOT start unrelated backend work.

This task is complete only when the real frontend visually resembles
the 9 images under docs/ui/ and existing functionality still works.