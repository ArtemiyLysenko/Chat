# ADR 0005: Modular Monolith With Protocol Adapters

## Status
Accepted

## Context
The project must deliver a classic web chat application on the governed stack of Java 25, Gradle, Spring Boot 4.0.3, PostgreSQL, WebSocket support, and local filesystem storage.
The scope now includes mandatory Jabber/XMPP client support, federation between two servers, and Jabber-specific admin observability screens.

This creates a key architecture choice:
- split the system into multiple independently deployed services
- introduce a separate primary XMPP server product and integrate the web app around it
- keep one Spring Boot application per server instance and model HTTP, WebSocket, and XMPP as adapters over one shared domain

## Decision
Adopt a modular monolith architecture with protocol adapters.

That means:
- each deployed server instance is one Spring Boot application backed by its own PostgreSQL database and local attachment storage
- the codebase is organized into internal modules for identity, rooms, direct messaging, moderation, messaging, attachments, presence, XMPP and federation, and admin observability
- HTTP, WebSocket, and Jabber/XMPP are adapters over the same application services and core domain rules
- federation is implemented by running two identical server nodes rather than by splitting business logic into separate services
- the same Spring Boot-served web UI remains the admin and user surface for the MVP

## Why
- The project already has unavoidable distributed complexity because federation is mandatory. Adding microservices on top of that would increase coordination, consistency, and deployment risk without solving a demonstrated scale problem.
- The main business rules are tightly coupled: authentication, room membership, bans, unread state, presence, message history, and attachment authorization must stay consistent across web and XMPP clients.
- A modular monolith keeps one authoritative domain model while still allowing clean internal boundaries and testable adapter seams.
- The local delivery contract is `docker compose` from the repository root. One deployable per node is the simplest shape that still supports the required two-server federation scenario.
- Spring Boot already fits the required delivery modes: HTTP, WebSocket, persistence, admin endpoints, and a server-rendered or static web UI shell.

## Consequences
- New code should be added behind internal module boundaries and application services, not as protocol-specific business logic embedded in controllers or gateways.
- XMPP integration should translate stanzas into the same internal commands and events used by the browser-facing flows.
- The compose strategy must support both a normal single-node development stack and a two-node federation validation stack.
- PostgreSQL remains the source of truth for durable state. In-memory state should be limited to live connections, session activity, and short-lived fan-out concerns.
- Redis stays out unless evidence from presence or federation validation proves it necessary.
- A separate frontend module or microservice split would require a follow-up ADR and concrete evidence that the modular monolith is no longer the right fit.
