# Milestone 7.1 Evidence

- Date: 2026-04-22
- Requirement slice: governed in-process XMPP client connectivity only
- Status: verified, without claiming Milestone 7.2 federation or Milestone 8 admin dashboards

## Scope Covered
- XMPP login against the same governed account model used by HTTP sessions
- Denial for tombstoned users
- Basic XMPP available or unavailable presence between connected friends
- Local one-to-one direct-message interoperability mapped to existing direct dialogs and messaging rules

## Code And Config Validated
- `modules/adapters/xmpp` now contains the in-process TCP adapter, session registry, XMPP XML helpers, and direct-message relay.
- `modules/features/identity` now exposes `UserDirectoryQuery` so the XMPP adapter authenticates through the existing identity service instead of duplicating password or deletion rules.
- `modules/adapters/persistence-jpa` now supports username lookup needed by governed XMPP login.
- `compose.yaml`, `apps/api/Dockerfile`, and `apps/api/src/main/resources/application.yml` now expose and configure the XMPP listener for the root single-node flow.

## Supported Protocol Level
- Kept the B3-reconciled narrow local slice only:
  - TCP stream open
  - SASL `PLAIN`
  - resource bind
  - available or unavailable presence
  - one-to-one chat messages to local JIDs only
- Explicitly not in scope for 7.1:
  - multi-user chat
  - XMPP-side room management
  - remote-domain federation
  - browser presence derivation from XMPP sessions

## Dependency Decision
- Primary source path:
  - Context7 resolved Smack as the relevant Java XMPP client library.
  - Context7 did not provide enough detail about the Java SE runtime bundle needed for Base64 and SASL bootstrap.
  - Official Maven Central metadata and the published `smack-java8` `4.4.8` POM were used to confirm the required Java runtime bundle.
- Chosen test-client set:
  - `org.igniterealtime.smack:smack-java8:4.4.8`
  - `org.igniterealtime.smack:smack-tcp:4.4.8`
  - `org.igniterealtime.smack:smack-im:4.4.8`
- Why:
  - `smack-java8` initializes the Java SE Base64 and hostname helpers that Smack expects at runtime.
  - Smack is used only for interoperability tests; business rules remain in the Spring Boot application.

## Verification Run
- Targeted verification:
  - Command: `./gradlew :apps:api:test --tests '*XmppIntegrationTests' --no-daemon`
  - Result: passed
  - Covered proof:
    - successful XMPP login for active users
    - tombstoned-user login denial
    - presence exchange between two connected friends
    - XMPP-to-core direct-message persistence
    - core-to-XMPP direct-message relay
- Full regression gate:
  - Command: `./gradlew test --no-daemon`
  - Result: passed

## Reviewer Pass And Fixes
- Root cause found during debugging:
  - Smack client authentication initially failed before our server auth logic because the Java SE Base64 encoder was never registered without the `smack-java8` runtime bundle.
- Minimal fixes applied after the review pass:
  - replaced the incomplete Smack test dependency set with `smack-java8`
  - sent an explicit closing stream tag on authentication failure
  - stopped masking connection failures with noisy disconnect cleanup in the test helper
  - tightened XMPP error mapping so malformed auth payloads fail authentication cleanly and unexpected runtime failures are not mislabeled as policy denials

## Bets Narrowed
- B3 is narrowed, not resolved:
  - the in-process adapter path is proven for local Jabber login, presence, and one-to-one direct messages
  - federation, persisted peer state, and load validation remain to be proven in later Milestone 7 and 8 slices

## Remaining Known Gaps
- XMPP presence is intentionally adapter-local in 7.1; browser presence remains tab-derived.
- Remote-domain routing, peer persistence, and traffic statistics are deferred to Milestones 7.2 through 8.1.
