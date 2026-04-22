# Milestone 8.2 Evidence

## Scope
- Close Milestone 8 with repeatable federation load tooling and mandatory advanced-scope validation.
- Prove 50-plus XMPP clients connected to node A and node B, bidirectional federated direct-message delivery, real post-run admin visibility, and clean peer or traffic telemetry under load.

## Code And Doc Changes Validated
- `tools/load-tests/federation` now contains the runnable Smack-based load harness, argument parsing, latency summaries, and targeted tests.
- `tools/load-tests/federation/build.gradle.kts` now runs the harness from the repository root so documented relative output paths land under the real repo `docs/evidence/`.
- Federation peer creation and cumulative traffic sampling in `modules/adapters/persistence-jpa` are now concurrency-safe under multi-client load.
- `infra/docker/compose.federation.yaml` carries the same `CHAT_AUTH_ADMIN_USERNAMES` support used by the Milestone 8.1 single-node path.

## Commands Run
- `COMPOSE_PROJECT_NAME=chat-federation-82 CHAT_AUTH_ADMIN_USERNAMES=admin docker compose -f infra/docker/compose.federation.yaml up -d`
- `./gradlew :modules:adapters:persistence-jpa:test --tests '*FederationRepositoryIntegrationTests' --no-daemon`
- `./gradlew :tools:load-tests:federation:test --no-daemon`
- `./gradlew :apps:api:test --tests '*XmppFederationIntegrationTests' --no-daemon`
- `./gradlew :tools:load-tests:federation:run --args='--client-count=50 --run-id=milestone82 --output=docs/evidence/2026-04-22-milestone-8-2-load-summary.json' --no-daemon`
- PostgreSQL verification queries on `chat_a` and `chat_b` for `federation_peers`, `federation_traffic_samples`, and `xmpp_client_sessions`
- `./gradlew test --no-daemon`

## Result Summary
- The targeted regression suites for `FederationRepositoryIntegrationTests`, `XmppFederationIntegrationTests`, and `tools:load-tests:federation:test` all passed after the concurrency and harness updates.
- The final `./gradlew test --no-daemon` run passed green for the whole repository.
- The final clean two-node run connected `50` XMPP clients to node A and `50` XMPP clients to node B.
- The harness provisioned `100` users per node and delivered all `100` federated direct messages successfully.
- `A_TO_B` finished `50/50` with `p95=337 ms`, `p99=348 ms`, `max=348 ms`, and `0` failures.
- `B_TO_A` finished `50/50` with `p95=388 ms`, `p99=421 ms`, `max=421 ms`, and `0` failures.
- Node A persisted peer traffic totals of `50` inbound messages, `50` outbound messages, `50` inbound stanzas, `50` outbound stanzas, and `0` federation errors for `node-b.local`.
- Node B persisted peer traffic totals of `50` inbound messages, `50` outbound messages, `50` inbound stanzas, `50` outbound stanzas, and `0` federation errors for `node-a.local`.
- `docker compose` app logs for the final run contained no `duplicate key value`, `DataIntegrityViolationException`, or other federation-delivery errors after the persistence fix.

## Saved Artifacts
- Load summary JSON: [2026-04-22-milestone-8-2-load-summary.json](2026-04-22-milestone-8-2-load-summary.json)
- Load run log: `2026-04-22-milestone-8-2-load-run.log`
- Federation admin screenshot: [2026-04-22-milestone-8-2-jabber-federation.png](2026-04-22-milestone-8-2-jabber-federation.png)
- Connections admin screenshot: [2026-04-22-milestone-8-2-jabber-connections.png](2026-04-22-milestone-8-2-jabber-connections.png)

## Browser Evidence Note
- Playwright MCP was attempted first for the post-run admin capture, but the browser backend still returned `Target page, context or browser has been closed`.
- The screenshots were therefore captured through a small authenticated local proxy plus headless Chrome, using the real admin HTML and API responses from node A after the clean final load run.

## Remaining Protocol Or Tooling Limit
- The load harness sends the XMPP stream-close marker and then force-closes the client socket so teardown stays bounded for 100-client runs.
- Delivery and federation counters remain clean, but the node-A and node-B connections dashboards each recorded a mixed post-run recent-session state of `12 DISCONNECTED` and `38 ERROR` because the current in-process adapter does not finish a clean close handshake fast enough for the harness path.
- This does not affect the mandatory load-validation requirement, but it remains a v1 telemetry limitation if cleaner Jabber session teardown reporting is needed later.

## Bets And Decisions
- B3 is resolved in favor of the existing governed in-process XMPP path.
- The persistence-side concurrency fix is now part of the accepted Milestone 8 implementation because the clean 50-per-side run depends on it for trustworthy peer and traffic telemetry.
