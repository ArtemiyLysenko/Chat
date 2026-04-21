# Architecture Baseline

This document defines the MVP architecture baseline for the classic web chat application.
It builds on ADR 0001 and should be read together with:
- `docs/adrs/0002-mvp-web-delivery-and-session-auth.md`
- `docs/adrs/0003-conversation-model-and-realtime-contract.md`
- `docs/adrs/0004-mandatory-xmpp-federation-scope.md`
- `docs/adrs/0005-modular-monolith-with-protocol-adapters.md`
- `docs/adrs/0006-hybrid-gradle-module-structure.md`
- `docs/api-contracts.md`
- `docs/persistence-model.md`
- `docs/mvp-delivery-plan.md`

## Scope And Architectural Boundary
- MVP scope is the required hackathon brief: authentication, rooms, direct messaging, contacts, moderation, attachments, persistent history, active sessions, unread state, presence, Jabber/XMPP client support, server federation, and Jabber-specific admin dashboards.
- The governing architecture shape is a modular monolith with protocol adapters: one Spring Boot deployable per server node, with HTTP, WebSocket, and XMPP integrated over shared application services.
- The runtime stays centered on the Spring Boot application served from `apps/api`, PostgreSQL, and local attachment storage under `storage/`, but the delivery scope must also support a multi-server federation deployment shape for validation.
- `docker compose up` from the repository root remains the primary local execution path.
- Redis and a dedicated standalone frontend remain outside the MVP architecture unless a later ADR or benchmark requires them.

## Runtime Topology
- Browser clients load the initial web UI from the Spring Boot app.
- HTTP endpoints handle authenticated commands, queries, uploads, and downloads.
- One authenticated WebSocket connection per tab carries low-latency server events and tab activity signals.
- A Java-compatible Jabber/XMPP integration layer maps XMPP clients and server-to-server federation onto the same chat domain.
- PostgreSQL is the source of truth for identities, sessions, rooms, friendships, messages, unread markers, moderation records, and attachment metadata.
- The local filesystem stores attachment binaries only. Database metadata controls access and lifecycle.
- Compose orchestration must support both the normal single-server developer path and a two-server federation validation path.

## Codebase Structure
- `apps/api` is the only deployable application and contains bootstrap, Spring wiring, HTTP, WebSocket, and UI entrypoints.
- `modules/core/*` contains shared technical primitives and test support.
- `modules/features/*` contains feature-local `api`, `domain`, `application`, `spi`, and `internal` packages.
- `modules/adapters/*` contains persistence, filesystem, and XMPP adapter implementations.
- `tools/load-tests/federation` contains federation validation scaffolding for the required two-node load scenario.

## Architected Domains

| Domain | Responsibilities | Source Of Truth |
| --- | --- | --- |
| Identity and sessions | Registration, login, logout, password lifecycle, persistent sessions, active session view and revocation, account deletion policy | PostgreSQL |
| Presence | Per-tab activity tracking, user online and AFK transitions, low-latency presence fan-out | PostgreSQL plus in-process connection state |
| Contacts and direct messaging | Friend requests, accepted friendships, user blocks, direct dialog eligibility | PostgreSQL |
| Rooms and moderation | Public catalog, private room membership, invitations, roles, bans, room deletion, moderation audit | PostgreSQL |
| Messaging and history | Message creation, edit, delete, reply links, unread markers, cursor-based history loading | PostgreSQL |
| Attachments and access control | Upload metadata, binary storage, download authorization, room-access revocation | PostgreSQL plus local filesystem |
| XMPP and federation | Jabber client interoperability, server-to-server routing, peer status, and federation traffic statistics | PostgreSQL plus XMPP runtime state |

## State Ownership
- PostgreSQL owns durable state and every permission decision that must survive reconnects or restarts.
- PostgreSQL `user_sessions` rows are the source of truth for browser authentication. The session cookie is only an opaque session id handle.
- The Spring Boot process owns ephemeral connection state: active WebSocket sessions, per-user live subscriptions, and a short-lived cache of recent tab activity.
- Presence is derived state, not its own durable business object. It is computed from authenticated session tabs plus recent activity timestamps.
- The filesystem owns file bytes, but never authorization. Every download request must re-check the caller's current rights in PostgreSQL.

## Cross-Cutting Rules
- Session-cookie authentication is the MVP default for both HTTP and WebSocket access.
- Commands are processed over HTTP. WebSocket is the fan-out channel for server events and the control channel for tab activity signals.
- Public rooms are discoverable and readable by authenticated users unless they are banned. Private rooms stay hidden from non-members except for invited users, who may load a pre-join preview that exposes only the room name and owner.
- Room membership uses one join path: `POST /api/rooms/{roomId}/join` handles both public joins and invited private-room joins.
- Direct dialogs are allowed only while both users are friends and neither has blocked the other.
- Users removed from or banned from a room lose access immediately to that room's message history, attachments, and future events.
- Removing a regular room member is remove-only. Removing an admin through member removal also creates a ban and records moderation events.
- XMPP-connected clients and federated peers must map onto the same authorization and history rules as the web UI.
- Message history uses cursor pagination from the start. Initial load returns the newest window; older windows are fetched by cursor and rendered chronologically.
- Room names are globally unique. Owners cannot leave their own room; they must delete it instead.
- Milestone 1 account deletion removes credentials, revokes sessions, tombstones the user row, and invokes `AccountDeletionImpactPort`. Milestone 2 now uses that hook to delete owned rooms, remove non-owned room memberships, clear room invites created by or for the deleted user, and clear bans targeting the deleted user. Surviving moderation and ban-actor references continue to point at the tombstoned `users` row.
- The web UI must expose Jabber administration screens for current connections and federation traffic statistics.

## Critical End-To-End Flows
1. Registration, login, logout, and session revocation
   The browser uses same-origin HTTP to register or authenticate, receives a persistent session cookie backed by `user_sessions`, opens a WebSocket with that cookie, and can later revoke a single session without affecting the others.
2. Room catalog, membership, and moderation
   Public rooms are discoverable and their full details are readable by authenticated users unless banned. Private rooms require invitation for join, and invited users only see the room name and owner before they join. Role changes, bans, removals, and room deletion must update database state consistently, with member removal keeping regular-member removal separate from admin removal plus ban.
3. Friendship to direct-dialog eligibility
   A direct dialog exists only for an accepted friendship with no active block on either side. Blocking a user terminates the friendship relation and freezes the existing direct-dialog history.
4. Message send, edit, delete, and unread updates
   Writes land in PostgreSQL first, then emit WebSocket events to all still-authorized sessions. Message edits and deletions are represented as state changes rather than row removal in the normal path.
5. Attachment upload and download authorization
   Uploads write metadata to PostgreSQL and file bytes to `storage/`. Downloads always re-check current room or direct-dialog eligibility so access revocation is immediate.
6. Multi-tab presence transitions
   Each tab reports activity with a stable client tab key. A user is `ONLINE` when at least one tab is active, `AFK` when connected tabs exist but all are inactive for more than one minute, and `OFFLINE` when no tab remains connected within the configured timeout window.
7. XMPP client connectivity
   A user can connect through a Jabber/XMPP client using the chosen interoperability level, authenticate against the governed account model, and exchange messages through the same chat domain.
8. Federated server traffic
   Two independently configured server instances exchange eligible messages in both directions while preserving delivery, authorization, and observability guarantees.
9. Jabber administration and federation insight
   Administrators can inspect current Jabber/XMPP connections and federation traffic statistics from the web UI without leaving the governed application surface.

## Delivery Sequence
The architected implementation order is fixed for the MVP:
1. Identity, password lifecycle, and active sessions
2. Room catalog, membership, and moderation
3. Friendships, blocks, and direct-dialog eligibility
4. Realtime messaging, unread markers, and history pagination
5. Attachments and access revocation
6. Presence and multi-tab behavior
7. Jabber/XMPP client support and inter-server federation
8. Jabber admin dashboards and two-server federation load validation

Each slice must leave behind updated docs, fresh validation evidence under `docs/evidence/`, and a bet or ADR review before the next slice starts. Detailed exit criteria live in `docs/mvp-delivery-plan.md` and `docs/governance/checklist.md`.

## Current Bets
- B2 tracks whether the MVP can stay on PostgreSQL plus in-process live state without Redis.
- B3 tracks which Java-compatible XMPP library and federation integration shape are the most pragmatic mandatory path.
