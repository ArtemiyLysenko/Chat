# Milestone 1: Identity, Password Lifecycle, And Active Sessions

## Summary
This milestone establishes the foundation for every later slice: database-backed identity, persistent session cookies, CSRF protection, password lifecycle flows, and the first real authenticated UI shell.
It also resolves account deletion to tombstone preservation and delivers the time-boxed B3 XMPP decision spike.

## Deliverables
- User registration, login, logout, password change, password reset request, password reset consume, active-session listing, selective session revocation, and account deletion entry point.
- Static unauthenticated pages for login, register, password reset request, and password reset consume.
- Static authenticated pages for the app shell and sessions management.
- Spring Security, Flyway, Bean Validation, Jackson, and Testcontainers added to the project and wired for reuse in later milestones.
- A B3 decision artifact that recommends the XMPP integration path to use in Milestone 7.

## Out Of Scope
- Real email delivery, mail sandbox infrastructure, and production SMTP integration.
- WebSocket session revocation enforcement. Milestone 1 prepares the hook; Milestone 4 applies it once `/ws` exists.
- Cross-feature account deletion cleanup beyond identity-side tombstoning and no-op cleanup hooks.

## Implementation Plan
1. Build and application foundation
   - Add `spring-boot-starter-security`, `spring-boot-starter-validation`, Flyway, and Testcontainers-backed PostgreSQL testing dependencies in the version catalog and application build.
   - Add security and persistence configuration under `apps/api/src/main/java/edu/artemiy/chat/app/config`.
   - Keep the cookie name configurable through properties, with `CHAT_SESSION` as the default.
2. Identity feature module
   - Implement identity-side `api`, `application`, and `spi` packages in `modules/features/identity`.
   - Introduce command and query use cases for register, login, logout, list sessions, revoke session, change password, request reset token, consume reset token, and delete account.
   - Add SPI contracts for:
     - user persistence
     - session persistence
     - password reset token persistence
     - password hashing
     - reset notification delivery
     - authenticated-user resolution
     - `AccountDeletionImpactPort`
3. Database and persistence adapter
   - Add forward Flyway migrations for `users`, `password_reset_tokens`, and `user_sessions`.
   - Extend `users` with `display_name` and `deleted_at`.
   - Implement JPA entities and repositories in `modules/adapters/persistence-jpa/identity`.
   - Store password hashes only; never store raw passwords or raw reset tokens.
   - Store reset token hashes with expiry and one-time-use semantics.
4. Security model
   - Use a custom cookie-auth filter backed by `user_sessions` as the source of truth.
   - Treat the session cookie value as an opaque UUID session id.
   - Use same-origin cookie auth with `HttpOnly` enabled and `SameSite=Lax`.
   - Set `Secure=true` outside local development profiles.
   - Add a JS-readable CSRF cookie named `XSRF-TOKEN` and require `X-CSRF-TOKEN` on state-changing browser requests.
5. Account deletion policy
   - Resolve B4 here: preserve historical non-owned messages through a tombstoned user identity.
   - On delete account:
     - revoke all active sessions
     - clear login credentials
     - replace `email` and `username` with unique tombstone values
     - set `display_name` to `Deleted user`
     - set `deleted_at`
     - invoke `AccountDeletionImpactPort` for cross-feature cleanup hooks
   - Do not delete the `users` row.
6. HTTP and static UI wiring
   - Implement controllers in `apps/api/src/main/java/edu/artemiy/chat/app/http`.
   - Keep UI assets under `apps/api/src/main/resources/static` with vanilla JavaScript modules only.
   - Replace the current placeholder bootstrap page with:
     - an unauthenticated entry surface
     - auth pages
     - an authenticated shell page that can later host rooms, contacts, and chat UI
   - Provide session-management UI for listing and revoking sessions.
7. Reset-delivery adapter
   - Add a logging reset-notification adapter for local, docker-local, and test profiles.
   - The adapter must log the raw reset URL or token once at issuance time and never persist the raw token.
8. B3 XMPP decision spike
   - Time-box the spike to one working day during this milestone.
   - Produce a dated evidence note or ADR candidate that compares:
     - same-process XMPP adapter path
     - companion or embedded server path if needed
     - fit with Spring Boot, the compose workflow, governed auth model, and admin observability needs
   - The spike output must recommend one implementation path for Milestone 7 and define rejection criteria for the discarded options.

## Contract And Behavior Locks
- `POST /api/auth/register`
  Request: `email`, `username`, `password`
- `POST /api/auth/login`
  Request: `email`, `password`
- `POST /api/auth/logout`
  Behavior: revoke current session only
- `GET /api/sessions`
  Response: array of `SessionSummary`
- `DELETE /api/sessions/{sessionId}`
  Behavior: must support revoking non-current sessions and revoke the current session if explicitly requested
- `POST /api/auth/password/change`
  Request: `currentPassword`, `newPassword`
  Behavior: keep current session active and revoke all other sessions
- `POST /api/auth/password/reset-requests`
  Request: `email`
  Behavior: return success even for unknown email to avoid account enumeration
- `POST /api/auth/password/reset`
  Request: `token`, `newPassword`
  Behavior: revoke all sessions after success
- `DELETE /api/account`
  Request: authenticated session plus `currentPassword`
  Behavior: revoke all sessions, tombstone identity, and execute cleanup hooks

## Tests And Evidence
- Automated
  - Flyway migration bootstrap against PostgreSQL from an empty schema
  - repository tests for uniqueness, token expiry, one-time reset use, and session revocation semantics
  - service tests for registration, login, logout, selective session revocation, password change, password reset, and account deletion
  - HTTP integration tests for cookie issuance, CSRF enforcement, redirect rules, auth-page access rules, and session listing
- Manual
  - two-browser proof that revoking one session leaves another active
  - browser-restart proof that login persists
  - reset-flow proof using the logged reset token or URL
  - screenshot or note showing the sessions management UI
  - B3 spike note with recommendation and decision criteria

## Exit Criteria
- B4 is moved to the resolved section of `docs/bet-register.md`.
- `docs/api-contracts.md`, `docs/persistence-model.md`, and `docs/architecture.md` match the implemented auth and deletion model.
- The milestone ends with a green `./gradlew test` and a dated evidence note in `docs/evidence/`.
