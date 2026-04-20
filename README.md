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
This repository now includes an agent-ready planning baseline and a runnable local compose contract.
The current application service is an intentional Spring Boot placeholder that keeps the root execution path concrete while the real chat workflow is scaffolded.

## Repository Layout
- `apps/api` Spring Boot application and initial web delivery
- `apps/web` reserved for a future dedicated frontend only if a later ADR justifies splitting it out
- `modules/` internal Gradle library modules for the modular monolith core, features, and adapters
- `tools/load-tests/federation` operational validation module for federation and multi-client test scaffolding
- `build-logic` included Gradle build for Kotlin convention plugins
- `docs/` delivery, architecture, governance, and evidence artifacts
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
- [Evidence guide](docs/evidence/README.md)
- [ADR 0001](docs/adrs/0001-initial-stack-direction.md)
- [ADR 0004](docs/adrs/0004-mandatory-xmpp-federation-scope.md)
- [ADR 0005](docs/adrs/0005-modular-monolith-with-protocol-adapters.md)
- [ADR 0006](docs/adrs/0006-hybrid-gradle-module-structure.md)

## Governed Stack
- Java 25
- Gradle 9.4.1
- Spring Boot 4.0.3
- PostgreSQL

## Local Bootstrap
1. Copy `.env.example` to `.env` if you want to override defaults.
2. Run `docker compose up --build` from the repository root.
3. Open `http://localhost:8080` for the placeholder web shell and `http://localhost:8080/actuator/health` for the health endpoint.

## Immediate Next Step
Replace the placeholder routes and page with the actual chat domain model, authentication flow, realtime messaging, moderation features, Jabber/XMPP support, federation, and the required admin observability screens from the hackathon brief.
