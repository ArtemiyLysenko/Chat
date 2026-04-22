# Chat

AI-first hackathon project for the AI Herders Jam.

The target product is a classic web chat application with:
- registration and authentication
- public and private rooms
- friend-based direct messaging
- online, AFK, and offline presence
- moderation and administration
- file and image sharing
- persistent history
- multi-session and multi-tab behavior
- Jabber/XMPP client connectivity
- federation between servers
- admin screens for Jabber connections and federation traffic

## Current Status
Milestones 1 through 8 are completed and verified.
The repository now ships a runnable Spring Boot plus PostgreSQL compose stack with browser registration, login, logout, password lifecycle flows, active-session management, room catalog and membership, private-room invite preview and join, moderation and ban management, room deletion, rooms-side and contacts-side account deletion cleanup, friendship request creation by username or user id, explicit accept and reject flows, reverse-direction auto-accept, friend removal, user block and unblock flows, centralized direct-message eligibility rules, stable direct-dialog ensure or fetch, HTTP room and direct-dialog history reads, forward-only unread markers, reply-capable sends, message edit and logical delete, live room and direct-dialog browser updates over the authenticated `/ws` channel, reconnect-driven HTTP refresh after socket recovery, live browser redirect when the current session is revoked, full Milestone 5 attachment delivery with message-bound room and direct-dialog uploads, paste-image handling, request-time access revocation, authorized downloads, room-delete blob cleanup, full Milestone 6 presence delivery with per-tab `/ws` activity tracking, `presence.updated` fan-out, and live presence badges in contacts and shared-room member views, completed Milestone 7 in-process Jabber login, tombstoned-user denial, local and federated one-to-one direct-message interoperability mapped to existing direct dialogs, blocked or otherwise ineligible XMPP denial aligned with the HTTP eligibility rules, persisted XMPP client-session or federation-peer or traffic telemetry, the governed two-node federation compose topology, and completed Milestone 8 admin-only Jabber dashboards plus repeatable two-node federation load validation with 50 connected clients per side and saved evidence under `docs/evidence/`.

## Repository Layout
- `apps/api` Spring Boot application and initial web delivery
- `apps/web` reserved for a future dedicated frontend only if a later ADR justifies splitting it out
- `modules/` internal Gradle library modules for the modular monolith core, features, and adapters
- `tools/load-tests/federation` operational validation module for federation and multi-client test scaffolding
- `build-logic` included Gradle build for Kotlin convention plugins
- `docs/` delivery, architecture, governance, and evidence artifacts
- `docs/milestones/` decision-complete implementation plans for each delivery milestone
- `gradle/libs.versions.toml` shared dependency and plugin version catalog
- `infra/docker` container-related assets
- `storage/` local file storage for uploads and previews
- `tmp/` temporary generated artifacts

## Operating Model
The repo follows ADLC: intent, generation, validation, governance, deployment, and observation run as a loop rather than as a strict sequence.

See:
- [ADLC operating model](docs/adlc-operating-model.md)
- [Bet register](docs/bet-register.md)
- [Architecture baseline](docs/architecture.md)
- [API contracts](docs/api-contracts.md)
- [Persistence model](docs/persistence-model.md)
- [MVP delivery sequence](docs/mvp-delivery-plan.md)
- [Milestone plans](docs/milestones/README.md)
- [Evidence guide](docs/evidence/README.md)
- [ADR 0001](docs/adrs/0001-initial-stack-direction.md)
- [ADR 0004](docs/adrs/0004-mandatory-xmpp-federation-scope.md)
- [ADR 0005](docs/adrs/0005-modular-monolith-with-protocol-adapters.md)
- [ADR 0006](docs/adrs/0006-hybrid-gradle-module-structure.md)

## Governed Stack
- Java 25
- Gradle 9.4.1
- Spring Boot 4.0.5
- PostgreSQL

## Local Bootstrap
1. Copy `.env.example` to `.env` if you want to override defaults.
2. Run `docker compose up --build` from the repository root.
3. Open `http://localhost:8080/` for the unauthenticated entry surface, `http://localhost:8080/actuator/health` for the health endpoint, and use `localhost:5222` for local XMPP client connections unless you override `XMPP_PORT`.

## Compose Defaults
- The default root-level Compose flow exposes the web app on `APP_PORT`, the XMPP adapter on `XMPP_PORT`, and keeps PostgreSQL internal to the Compose network.
- This avoids host-port conflicts on `5432` and makes `docker compose up` from the repository root more reliable for reviewers.
- If you need interactive database access, use `docker compose exec db psql -U ${POSTGRES_USER:-chat} -d ${POSTGRES_DB:-chat}` from the repository root.

## Immediate Next Step
Current scoped delivery is complete. If follow-on work is needed, the next pragmatic hardening step is the XMPP stream-close path so forced load-harness disconnects appear as clean disconnects in admin session telemetry.
