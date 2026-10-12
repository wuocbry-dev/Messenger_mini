# Frontend Agent Notes

- Use Vite, React, and TypeScript.
- Keep code feature-based under `src/features`.
- Put app wiring in `src/app`.
- Put reusable API helpers, hooks, components, types, and utilities in `src/shared`.
- Do not expose backend secrets through `VITE_*` variables.
- Agora Web SDK integration belongs under `src/features/call`.
- Do not place every feature API call in one giant services file.
