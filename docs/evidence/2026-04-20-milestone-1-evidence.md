# Milestone 1 Evidence

## Date
2026-04-20

## Scope
Milestone 1 identity and session foundation:
- registration
- login and logout
- active session listing and selective session revocation
- password change
- password reset request and consume
- account deletion entry with tombstoned-user behavior
- static unauthenticated and authenticated pages
- B3 XMPP path spike output

## Sources Reviewed
- `docs/milestones/01-identity-sessions.md`
- `docs/mvp-delivery-plan.md`
- `docs/api-contracts.md`
- `docs/persistence-model.md`
- `docs/architecture.md`
- `docs/bet-register.md`
- `docs/governance/checklist.md`

## Artifacts Updated
- `apps/api` Spring Boot wiring for security, Flyway, cookie auth, CSRF, HTTP controllers, and static pages
- `modules/features/identity` use cases, domain rules, and SPI contracts
- `modules/adapters/persistence-jpa` Flyway migrations, JPA adapters, password hashing, reset delivery, and no-op account deletion impact adapter
- `modules/core/testing` PostgreSQL Testcontainers support
- `docs/api-contracts.md`
- `docs/architecture.md`
- `docs/persistence-model.md`
- `docs/bet-register.md`
- `docs/evidence/2026-04-20-b3-xmpp-path-spike.md`

## Verification Commands

### Automated verification
- `./gradlew :modules:features:identity:test`
  Result: passed
- `./gradlew :modules:adapters:persistence-jpa:test --tests '*FlywayMigrationBootstrapTests'`
  Result: passed
- `./gradlew :modules:adapters:persistence-jpa:test --tests '*IdentityRepositoryIntegrationTests'`
  Result: passed
- `./gradlew :apps:api:test --tests '*ChatApplicationTests' --tests '*ApplicationHttpWiringTests'`
  Result: passed
- `./gradlew test`
  Result: passed

### Deployment verification
- `docker compose -f /Users/artemiy/Projects/Chat/compose.yaml build app`
  Result: passed, `chat-app:latest` built successfully
- `/bin/zsh -lc "APP_PORT=18080 POSTGRES_PORT=15432 docker compose -f /Users/artemiy/Projects/Chat/compose.yaml up -d"`
  Result: passed, `chat-db-1` and `chat-app-1` started
- `docker compose -f /Users/artemiy/Projects/Chat/compose.yaml ps`
  Result: `chat-db-1` healthy and `chat-app-1` running with published ports
- `docker run --rm --network chat_default curlimages/curl:8.12.1 -i --max-time 10 http://app:8080/actuator/health`
  Result: `HTTP/1.1 200` with `{"groups":["liveness","readiness"],"status":"UP"}`
- `docker run --rm --network chat_default curlimages/curl:8.12.1 -i --max-time 10 http://app:8080/`
  Result: `HTTP/1.1 200` and the Milestone 1 entry page HTML
- `curl -i --max-time 10 http://localhost:18080/actuator/health`
  Result: `HTTP/1.1 200` with `{"groups":["liveness","readiness"],"status":"UP"}` from the host environment outside the sandbox
- `curl -i --max-time 10 http://localhost:18080/`
  Result: `HTTP/1.1 200` and the Milestone 1 entry page HTML from the host environment outside the sandbox
- `/bin/zsh -lc "APP_PORT=18080 POSTGRES_PORT=15432 docker compose -f /Users/artemiy/Projects/Chat/compose.yaml down -v"`
  Result: passed, containers, network, and temporary volume removed

### Root-cause debugging commands used during verification
- `jps -lvm`
  Result: identified the stuck Gradle test worker JVM
- `jcmd 84212 Thread.print`
  Result: confirmed the worker was blocked in Docker socket reads during Testcontainers startup
- `curl --max-time 5 --unix-socket /Users/artemiy/.docker/run/docker.sock http://localhost/_ping`
  Result: `OK`
- `curl --max-time 5 --unix-socket /Users/artemiy/Library/Containers/com.docker.docker/Data/docker.raw.sock http://localhost/_ping`
  Result: timed out

## Verification Findings And Fixes
- Spring Data JPA repository scanning in the app context was anchored to the bootstrap package instead of the persistence adapter package.
  Fix: added explicit JPA entity and repository configuration in `modules/adapters/persistence-jpa`.
- Testcontainers startup inherited a bad Docker socket choice from local user properties.
  Fix: test support and Gradle test conventions now prefer `~/.docker/run/docker.sock` and ignore stale user-level Testcontainers overrides.
- Spring Security 7 defaulted to XOR-masked CSRF request handling, which conflicted with the chosen `XSRF-TOKEN` cookie plus `X-CSRF-TOKEN` header browser flow.
  Fix: configured a plain `CsrfTokenRequestAttributeHandler` so static JS can send the raw cookie token as the header value.

## Decisions Captured
- B4 is resolved by executable implementation: account deletion tombstones the `users` row, revokes sessions, clears credentials, and invokes `AccountDeletionImpactPort`.
- B3 remains active but narrowed by `2026-04-20-b3-xmpp-path-spike.md`, which recommends Openfire as the Milestone 7 companion XMPP service.

## Remaining Risk
- None from Milestone 1 host-port reachability. The earlier localhost failure was specific to the sandboxed agent environment and was cleared by a fresh outside-sandbox host probe.
