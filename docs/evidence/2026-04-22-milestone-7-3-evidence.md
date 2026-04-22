# Milestone 7.3 Evidence

- Date: 2026-04-22
- Requirement slice: XMPP and federation hardening, blocked or otherwise ineligible direct-message denial over XMPP, and Milestone 7 closeout
- Status: verified, and Milestone 7 is now complete without claiming Milestone 8 admin dashboards or load validation

## Scope Covered
- Local XMPP direct messages now reject blocked or otherwise ineligible pairs through the same direct-message eligibility rules used by HTTP.
- Federated direct messages now surface remote policy rejections back to the sending XMPP client instead of collapsing every failure into a generic availability error.
- Federation telemetry now distinguishes:
  - successful inbound or outbound message delivery
  - inbound or outbound rejected stanzas against a reachable peer
  - transport or configuration failures that still mark the peer unavailable
- The current mirrored-local-username federation mapping remains sufficient for the mandatory Milestone 7 direct-message scope because both local and federated delivery reuse the existing direct-dialog rules.

## Code And Behavior Validated
- `modules/adapters/xmpp/XmppConnectionHandler`
  - now preserves local direct-message eligibility enforcement for local and federated XMPP sends
  - now maps remote federation rejections back to XMPP message errors
  - now keeps reachable peers `UP` for rejection-only traffic samples
- `modules/adapters/xmpp/XmppFederationGateway`
  - now surfaces the remote federation failure condition for caller-side mapping
- `modules/features/federation/FederationTelemetry`
  - now records rejected inbound and outbound federation stanzas separately from transport errors
- `apps/api` integration coverage now includes:
  - local XMPP denial for ineligible direct messages
  - federated XMPP denial for remote block scenarios

## Verification Run
- Targeted XMPP and federation verification:
  - Command: `./gradlew :modules:features:federation:test :apps:api:test --tests '*XmppIntegrationTests' --tests '*XmppFederationIntegrationTests' --no-daemon`
  - Result: passed in 50 seconds
  - Covered proof:
    - local ineligible direct messages over XMPP return `not-allowed`
    - federated direct messages denied by a remote block return `not-allowed`
    - denied federated traffic does not persist a message on the receiving node
    - denied federated traffic records stanza-level errors while leaving both peers `UP`
- Full regression gate:
  - Command: `./gradlew test --no-daemon`
  - Result: passed in 1 minute 10 seconds
- Two-node topology smoke proof:
  - Command: `docker compose -f infra/docker/compose.federation.yaml up -d --build`
  - Result: passed
  - Follow-up checks:
    - `docker compose -f infra/docker/compose.federation.yaml ps`
    - `curl -fsS http://localhost:8081/actuator/health`
    - `curl -fsS http://localhost:8082/actuator/health`
    - `docker compose -f infra/docker/compose.federation.yaml down`
  - Result: passed, with both app nodes and both databases healthy before teardown

## Reviewer Pass And Fixes
- Reviewer issue found:
  - remote federation `forbidden` responses were being collapsed into `service-unavailable`, which would misclassify a reachable rejection as a peer outage
- Fix applied:
  - preserved `forbidden` as a rejection condition so reachable-peer denials stay aligned with the actual XMPP outcome and with the federation telemetry model

## Milestone 7 Closeout
- Milestone 7 exit criteria are now met:
  - one XMPP client can log in and exchange eligible direct messages
  - blocked or otherwise ineligible direct-message denial is covered for local and federated XMPP delivery
  - two servers can federate direct messages in both directions
  - the milestone ends with a fresh green targeted verification run, a fresh green `./gradlew test --no-daemon`, and this dated evidence note

## Bets Narrowed
- B3 remains active, not resolved:
  - the in-process Spring Boot XMPP path is now proven through Milestone 7, including denial hardening and persisted federation telemetry
  - Milestone 8.2 still needs the required 50-plus-clients-per-side federation load evidence before B3 can close

## Remaining Known Gaps
- Jabber admin endpoints and classic-web dashboards still need real data wiring and authorization proof in Milestone 8.1.
- The required two-node 50-plus-clients-per-side federation load run is still pending for Milestone 8.2.
