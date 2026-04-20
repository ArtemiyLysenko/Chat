# ADR 0006: Hybrid Gradle Module Structure

## Status
Accepted

## Context
ADR 0005 selected a modular monolith with protocol adapters.
The remaining implementation choice was how strongly to encode those boundaries in the codebase:
- keep everything inside `apps/api` with package-only boundaries
- split into multiple deployables or microservices
- keep one deployable app, but move domain and adapter concerns into internal Gradle library modules

## Decision
Adopt a hybrid Gradle module structure:
- `apps/api` remains the only Spring Boot deployable
- shared technical primitives live in `modules/core/*`
- business capabilities live in `modules/features/*`
- infrastructure adapters live in `modules/adapters/*`
- operational load-test scaffolding lives in `tools/load-tests/*`

The governed target layout is:
- `apps/api`
  The only bootable application. It owns bootstrap, Spring configuration, HTTP controllers, WebSocket handlers, and web UI entrypoints.
- `modules/core/kernel`
  Cross-cutting technical primitives only, such as base result types, domain events, clock ports, and shared error contracts.
- `modules/core/testing`
  Shared test fixtures and architecture-test helpers.
- `modules/features/identity`
- `modules/features/rooms`
- `modules/features/contacts`
- `modules/features/messaging`
- `modules/features/attachments`
- `modules/features/presence`
- `modules/features/federation`
- `modules/features/admin`
  Each feature module owns its own `api`, `domain`, `application`, `spi`, and `internal` packages.
- `modules/adapters/persistence-jpa`
  JPA entities, Spring Data repositories, and schema migrations organized by feature.
- `modules/adapters/storage-filesystem`
  Attachment storage implementation on the local filesystem.
- `modules/adapters/xmpp`
  Jabber/XMPP client interoperability and server-to-server federation adapter code.
- `tools/load-tests/federation`
  Two-node federation and multi-client validation scaffolding.

Dependency direction is governed as follows:
- `apps/api` may depend on feature `api`, on feature `spi` where wiring needs those ports, and on adapter implementations.
- Feature modules may depend on `modules/core/kernel` and on other features' `api` packages only.
- Feature modules must never depend on adapter modules.
- Adapter modules may depend on feature `api` and `spi`, but never the reverse.
- Cross-feature access must go through feature `api` packages rather than `domain`, `application`, `spi`, or `internal`.

## Why
- Package-only boundaries are too soft for the mandatory XMPP, federation, and admin-observability scope.
- Multiple Gradle modules give stronger dependency control and testable architecture rules without introducing operational microservice complexity.
- One bootable application still preserves the simplest local and federation deployment model.
- This structure keeps transport concerns thin while ensuring HTTP, WebSocket, and XMPP all execute the same business rules through shared feature services.
- The split is strong enough to support architecture tests and future extraction decisions, while still keeping the repo pragmatic for hackathon delivery.

## Consequences
- New business logic should default into feature modules, not into `apps/api`.
- `apps/api` should contain bootstrap, configuration, HTTP, WebSocket, and UI wiring only.
- Architecture tests are part of the build and enforce the intended dependency direction.
- Future splits into separate deployables require a new ADR rather than ad hoc module growth.
- Shared business types must live in the owning feature module rather than in a generic `common` package.
- The XMPP implementation is an adapter over shared feature contracts, not a second business core.
- Persistence and storage details stay behind adapter modules so feature code remains independent of JPA and filesystem concerns.
