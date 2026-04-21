# Milestone 8: Jabber Admin Dashboards And Federation Load Validation

## Summary
This milestone turns the mandatory XMPP and federation implementation into an observable, reviewable deliverable.
It replaces the placeholder admin endpoints with real data and produces the strongest required two-server load evidence.

## Deliverables
- Admin connection dashboard for current and recent XMPP client sessions.
- Federation peer status view.
- Federation traffic statistics view.
- Repeatable two-server federation load-validation tooling and artifacts.

## Out Of Scope
- General-purpose admin console beyond the Jabber and federation surfaces required by the brief.
- Horizontal multi-node scaling beyond the two-server validation target.

## Implementation Plan
1. Real admin queries
   - Replace the current placeholder Jabber admin data in the federation and admin feature modules with real queries over:
     - `xmpp_client_sessions`
     - `federation_peers`
     - `federation_traffic_samples`
   - Keep the existing admin feature and federation feature separation intact.
2. Authorization
   - Add explicit admin authorization for the Jabber dashboard endpoints and UI routes.
   - Replace the Milestone 1 temporary `denyAll` rule on `/api/admin/**` with the real admin authorization model.
   - Reuse the existing security stack from Milestone 1.
3. Admin UI
   - Implement concise classic-web admin screens inside the Spring Boot-served UI.
   - Provide:
     - current and recent client connections
     - peer health or status
     - traffic counters and last-sampled timestamps
4. Load tooling
   - Implement the load-test driver in `tools/load-tests/federation` using the chosen XMPP stack or a compatible client harness.
   - The tool must be able to:
     - connect 50 or more clients to server A
     - connect 50 or more clients to server B
     - exchange messages in both directions
     - record topology, counts, timings, and errors
5. Repeatability
   - Document how to run the two-node environment and the load test.
   - Keep the artifacts under `docs/evidence/` using the project naming convention.

## Contract And Behavior Locks
- Fully implement:
  - `GET /api/admin/jabber/connections`
  - `GET /api/admin/jabber/federation/peers`
  - `GET /api/admin/jabber/federation/traffic`
- Use `JabberConnectionStatus`, `FederationPeerStatus`, and `FederationTrafficSnapshot` as the public types returned by those endpoints.
- Keep these views admin-only.

## Tests And Evidence
- Automated
  - integration tests for admin authorization and dashboard query responses
  - tests for traffic aggregation or sampling queries
- Manual and benchmark
  - screenshots of both admin screens
  - logs or artifacts for the 50-plus-clients-per-side federation run
  - a concise note stating any protocol-level or tooling limits that remain in v1

## Exit Criteria
- The mandatory advanced scope is fully evidenced.
- The repo contains repeatable two-node federation validation assets and real admin observability screens.
- The milestone ends with a green `./gradlew test` and a dated evidence note in `docs/evidence/`.
