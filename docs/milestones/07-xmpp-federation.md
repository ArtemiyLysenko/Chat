# Milestone 7: Jabber/XMPP Client Support And Inter-Server Federation

## Summary
This milestone delivers the mandatory XMPP and federation scope for the first supported protocol slice: one-to-one chat plus basic presence.
It must follow the B3 decision artifact produced during Milestone 1 and must not reopen the library choice unless that earlier evidence is shown to be invalid.

## Deliverables
- XMPP client authentication against the governed account model.
- Basic XMPP presence and one-to-one direct-message interoperability.
- Two-server federation for direct messages in both directions.
- Persisted peer and client-session state for later admin dashboards.

## Fixed Scope For v1
- Support XMPP authentication, basic presence, and one-to-one chat only.
- Do not implement XMPP-side room management or multi-user chat.
- Keep rooms browser-first in v1.

## Implementation Plan
1. Use the Milestone 1 B3 decision artifact
   - Implement the exact XMPP approach selected there.
   - If the B3 evidence is invalidated, record a new ADR or evidence note before proceeding with implementation.
2. XMPP adapter
   - Implement the XMPP translation layer in `modules/adapters/xmpp`.
   - Translate inbound XMPP auth, presence, and one-to-one messages into the existing identity, contacts, messaging, and presence feature services.
   - Translate internal chat events back into the supported XMPP output shape.
3. Database and persistence adapter
   - Add Flyway migrations for `xmpp_client_sessions`, `federation_peers`, and `federation_traffic_samples`.
   - Implement persistence support in `modules/adapters/persistence-jpa/federation`.
4. Auth and domain rules
   - Authenticate XMPP users against the same user and deletion rules as the web app.
   - Deny login for tombstoned users.
   - Enforce direct-message eligibility exactly as the HTTP path does: friendship required, no active block in either direction.
   - Reuse the same message persistence and unread logic where applicable.
5. Federation topology
   - Add two-node federation compose assets under `infra/docker`.
   - Keep the root `docker compose up` flow for single-node development unchanged.
   - Provide a documented path to bring up `app-a + db-a` and `app-b + db-b` for federation validation.
6. Metrics and observability capture
   - Count inbound and outbound messages, stanzas, and errors.
   - Persist peer status transitions and client session changes so Milestone 8 can query real data instead of placeholders.

## Contract And Behavior Locks
- There is no new browser REST API beyond the existing admin endpoints.
- The XMPP support level and supported stanza set must be documented by the B3 artifact and echoed in the evidence for this milestone.
- XMPP messages map only to direct dialogs in v1.
- Federation must preserve the same direct-message eligibility rules as local HTTP sends.

## Tests And Evidence
- Automated
  - integration tests for XMPP login success and failure
  - integration tests for one-to-one XMPP message exchange against the chosen adapter path
  - integration tests for blocked or ineligible direct-message denial over XMPP
  - two-node federation tests for A-to-B and B-to-A direct-message delivery
- Manual
  - evidence note naming the chosen library or integration path and supported protocol level
  - proof that one XMPP client can log in and exchange eligible direct messages
  - federation proof with both servers active

## Exit Criteria
- One XMPP client can log in and exchange eligible direct messages.
- Two servers can federate direct messages in both directions.
- The milestone ends with a green `./gradlew test` and a dated evidence note in `docs/evidence/`.
