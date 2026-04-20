# ADR 0001: Initial Stack Direction

## Status
Accepted

## Context
The repository is empty and the brief requires a classic web chat application that must run locally via `docker compose up`. The hackathon emphasizes agent-led delivery with minimal human coding, so the implementation stack should optimize for generation speed, coherence, and local operability.
This ADR now records the governed stack decision for the project.

## Decision
Build the solution on:

- Java 25
- Gradle 9.4.1
- Spring Boot 4.0.3
- PostgreSQL for persistence
- Spring Boot websocket support for realtime chat and presence
- Local filesystem storage under `storage/`
- Docker Compose at the repository root for local orchestration

The initial bootstrap serves the placeholder web shell from the Spring Boot application itself. If the project later needs a dedicated standalone frontend, that split must be captured in a follow-up ADR without changing the governed backend stack.

## Why
- The user explicitly selected this stack as the delivery baseline.
- Spring Boot 4.0.3 official docs state Java 17 or higher is supported, which keeps Java 25 inside the supported range.
- Current Gradle compatibility docs support running Gradle on Java 25, so the build tool can align with the governed runtime instead of relying on an older daemon JVM.
- Spring Boot aligns well with the required feature set: HTTP APIs, websocket messaging, persistence, security, and operational endpoints.
- Local file storage matches the brief directly.
- PostgreSQL offers straightforward relational modeling for rooms, memberships, bans, friendships, sessions, and messages.

## Consequences
- The bootstrap and future implementation must use Java 25, Gradle, Spring Boot, and PostgreSQL.
- Temporary Node-based placeholders and manifests are out of scope and should not remain part of the execution path.
- Redis stays optional until evidence shows it is needed.
- Mandatory Jabber/XMPP support and federation must fit the governed stack and root-level `docker compose` workflow, even if their delivery is sequenced after the first core web-chat milestones.
