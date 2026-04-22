# Milestone 8.1 Evidence

- Date: 2026-04-22
- Requirement slice: real Jabber admin authorization, classic-web admin dashboards, and real dashboard query wiring
- Status: verified, without claiming the Milestone 8.2 load-validation closeout

## Scope Covered
- `GET /api/admin/jabber/connections` is now admin-only and returns real `xmpp_client_sessions` data.
- `GET /api/admin/jabber/federation/peers` is now admin-only and returns real `federation_peers` data.
- `GET /api/admin/jabber/federation/traffic` is now admin-only and returns real `federation_traffic_samples` data.
- The classic-web UI now exposes two protected admin screens:
  - `Jabber connections`
  - `Federation status`
- The root compose flow now accepts `CHAT_AUTH_ADMIN_USERNAMES` so the configured admin roster works in local container runs as well as the app process.

## Admin Authorization Choice
- Chosen approach:
  - a config-backed admin roster via `CHAT_AUTH_ADMIN_USERNAMES`
  - matching is case-insensitive against the authenticated username from the existing session-cookie flow
- Why this shape was chosen:
  - the milestone requires real admin authorization, but does not require admin-management CRUD
  - this keeps the security model explicit and reviewable without inventing a new user-role management surface
- Alternative not chosen:
  - persist mutable admin roles in PostgreSQL
  - this would be more flexible later, but it would add schema, management flows, and governance surface not required by the brief

## Code And UI Validated
- Security and auth wiring:
  - `ChatSessionAuthenticationFilter` now grants `ROLE_ADMIN` for configured admin usernames
  - `SecurityConfiguration` now enforces admin-only API and UI-route access and returns JSON `403` for API denials
- UI routes:
  - `UiRoutingController` now serves the protected Jabber admin routes
- Classic-web screens:
  - `jabber-connections.html` plus `jabber-connections-page.js`
  - `jabber-federation.html` plus `jabber-federation-page.js`
- Compose and local config:
  - `compose.yaml`
  - `.env.example`

## Verification Run
- Targeted admin wiring verification:
  - Command: `./gradlew :apps:api:test --tests '*ApplicationHttpWiringTests' --no-daemon`
  - Result: passed in 14 seconds
  - Covered proof:
    - authenticated non-admin users receive `403` on the admin APIs
    - authenticated non-admin users are redirected away from the admin UI routes
    - configured admin users can read real connections, peer, and traffic payloads
    - both admin HTML routes resolve successfully for an admin session
- Full regression gate:
  - Command: `./gradlew test --no-daemon`
  - Result: passed in 1 minute 2 seconds

## Browser And Screenshot Proof
- Isolated runtime:
  - Command: `COMPOSE_PROJECT_NAME=chat-admin-81 APP_PORT=8083 XMPP_PORT=5225 SPRING_PROFILES_ACTIVE=prod CHAT_AUTH_SECURE_COOKIE=false CHAT_AUTH_ADMIN_USERNAMES=admin81 docker compose up -d --build`
  - Follow-up checks:
    - `docker compose ps`
    - `curl -fsS http://localhost:8083/actuator/health`
  - Result: passed
- Real dashboard data seeding for the isolated environment:
  - inserted one XMPP client session for `admin81`
  - inserted one federation peer and one traffic sample for `peer-admin81.local`
- Captured screenshots:
  - [2026-04-22-milestone-8-1-jabber-connections.png](/Users/artemiy/Projects/Chat/docs/evidence/2026-04-22-milestone-8-1-jabber-connections.png)
  - [2026-04-22-milestone-8-1-jabber-federation.png](/Users/artemiy/Projects/Chat/docs/evidence/2026-04-22-milestone-8-1-jabber-federation.png)
  - Both images were captured at `1440x1200`
- Capture method note:
  - the Playwright MCP backend was unavailable in this session, so the protected-page screenshots were captured with deterministic headless Chrome commands against the isolated compose stack
  - a temporary same-origin helper page was used only during verification to establish an admin session for those screenshots, and it was removed before commit
- Teardown:
  - Command: `docker compose down`
  - Result: passed

## Reviewer Pass
- Checked:
  - exact admin-only protection for API and UI routes
  - admin authority derivation from the existing session-cookie authentication flow
  - classic-web page rendering against the real admin endpoints
  - compose-path truthfulness for the new admin-roster setting
- No additional reviewer-found defect remained after the final full test gate.

## Remaining Known Gaps
- Milestone 8.2 still needs the repeatable `tools/load-tests/federation` package and the required 50-plus-clients-per-side two-node run artifacts.
- README Current Status and Immediate Next Step remain unchanged in this slice because Milestone 8 is not closed yet.
