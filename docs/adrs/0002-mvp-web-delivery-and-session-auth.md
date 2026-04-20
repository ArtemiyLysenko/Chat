# ADR 0002: MVP Web Delivery And Session Authentication

## Status
Accepted

## Context
The brief requires a classic web chat experience with persistent browser login, active session management, selective session revocation, and a root-local `docker compose up` workflow.
The initial architecture left two questions open:
- should the MVP UI stay Spring Boot-served or move immediately to a dedicated frontend module
- should authentication use server-managed sessions or JWT-style tokens

## Decision
For the MVP:
- the initial web UI remains served by the Spring Boot application in `apps/api`
- authentication uses server-managed persistent sessions backed by PostgreSQL and exposed to the browser as same-origin session cookies
- the WebSocket handshake uses the same authenticated session cookie as the HTTP layer

## Why
- The project is a classic same-origin web application, not an API-only platform.
- Session cookies simplify active-session listing and selective revocation, which are first-class requirements in the brief.
- Server-managed sessions align better with password reset, browser persistence, and current-session-only logout semantics than JWT plus refresh-token plumbing.
- Keeping the UI inside the Spring Boot deployable reduces coordination overhead while the core product is still being built.

## Consequences
- CSRF protection must be part of the MVP security design because browser cookies are used for authentication.
- Browser login persistence and session revocation are implemented as database-backed session lifecycle operations, not token blacklist workarounds.
- If the project later splits into a dedicated frontend, that change needs a follow-up ADR, but it does not require a change to the governed backend stack.
- Authenticated WebSocket connections depend on the same session store as HTTP requests, so session revocation must also close or invalidate live socket connections.
