# Milestone 7.2 Evidence

- Date: 2026-04-22
- Requirement slice: persisted federation state, two-node direct-message federation, and the governed two-node compose topology
- Status: verified, without claiming Milestone 7.3 hardening or Milestone 8 admin dashboards

## Scope Covered
- `xmpp_client_sessions`, `federation_peers`, and `federation_traffic_samples` are now part of the forward-only PostgreSQL schema.
- The federation feature now persists Jabber client-session state, peer status, and cumulative traffic samples through a dedicated persistence port and JPA adapter.
- The in-process XMPP adapter now supports A-to-B and B-to-A one-to-one direct-message federation over the governed adapter boundary.
- `infra/docker/compose.federation.yaml` now defines the supported two-node validation topology while leaving the root single-node `compose.yaml` flow unchanged.

## Code And Config Validated
- `modules/features/federation` now exposes:
  - `FederationService`
  - `FederationSettings`
  - `FederationTelemetry`
  - `FederationPersistencePort`
  - `DefaultFederationService`
- `modules/adapters/persistence-jpa` now persists:
  - XMPP client sessions
  - federation peer status
  - federation traffic samples
- `modules/adapters/xmpp` now includes:
  - `FederationTransportProperties`
  - `XmppFederationGateway`
  - inbound federation authentication and federated-message handling in `XmppConnectionHandler`
- `infra/docker/compose.federation.yaml` now wires:
  - `app-a + db-a`
  - `app-b + db-b`
  - node-specific XMPP domains
  - peer host or port wiring
  - a non-blank shared secret
  - plain-HTTP-safe cookie settings for the compose validation path

## Current Federation Model
- The federation slice stays inside the single Spring Boot node per server decided by B3.
- Outbound peer delivery uses a narrow raw XML stream over the XMPP listener with a shared-secret federation auth stanza.
- Inbound peer messages are translated back into the existing contacts and messaging services, so direct-message eligibility and history persistence stay aligned with the HTTP path.
- Current v1 assumption:
  - each participating username exists locally on both nodes
  - inbound federated messages map onto those mirrored local users instead of introducing a separate remote-identity table
- This assumption is recorded as an open B3 follow-up for Milestone 7.3 rather than treated as a permanent architecture decision.

## Verification Run
- Targeted topology verification:
  - Command: `docker compose -f infra/docker/compose.federation.yaml config`
  - Result: passed
  - Covered proof:
    - both nodes expose independent HTTP and XMPP ports
    - each node has a distinct XMPP domain
    - each node points at the peer host and peer port with the same shared secret
    - the root single-node compose file remains untouched
- Targeted federation verification:
  - Command: `./gradlew :modules:features:federation:test :apps:api:test --tests '*XmppIntegrationTests' --tests '*XmppFederationIntegrationTests' --no-daemon`
  - Result: passed
  - Covered proof:
    - local governed XMPP login still works after the federation changes
    - A-to-B and B-to-A federated direct messages both deliver to active XMPP sessions
    - inbound federated messages persist in local direct-dialog history
    - admin observability queries now return real peer and traffic data
    - traffic snapshots record `inboundMessages = 1`, `outboundMessages = 1`, and `errorCount = 0` for each peer in the two-node test
- Full regression gate:
  - Command: `./gradlew test --no-daemon`
  - Result: passed

## Reviewer Pass And Fixes
- Root cause found during debugging:
  - the first federation gateway implementation created `XMLStreamReader` on the peer socket before writing the opening federation payload, which blocked on a read timeout and left the peer with an accepted but idle socket
- Minimal fixes applied after the review pass:
  - moved peer-stream reader creation until after the gateway flushes the opening stream plus auth payload
  - aligned the outbound federation stream-open shape with the working XMPP client framing
  - moved federation service wiring fully behind the federation feature API so the app module no longer depends on federation application or SPI packages
  - made federation shared-secret configuration explicitly non-blank for outbound or inbound federation handling
  - made auth-cookie security env-configurable so the two-node plain-HTTP compose path stays usable for later browser or admin validation

## Bets Narrowed
- B3 is narrowed again, not resolved:
  - one Spring Boot node can now handle local Jabber interoperability plus two-node direct-message federation and persisted federation telemetry
  - Milestone 7.3 still needs blocked or otherwise ineligible direct-message denial proof and final behavior hardening
  - Milestone 8.2 still needs the required 50-plus-clients-per-side federation load evidence

## Remaining Known Gaps
- Federated one-to-one delivery currently depends on mirrored local usernames across the participating nodes.
- Federation denial cases for blocked or otherwise ineligible direct messages are deferred to Milestone 7.3.
- The admin dashboards still show placeholders until Milestone 8.1 binds the real federation queries to the classic-web admin UI.
