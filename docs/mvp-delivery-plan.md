# MVP Delivery Plan

This document is the master sequence for the required chat scope, including the mandatory Jabber/XMPP and federation requirements.
Decision-complete implementation details live in `docs/milestones/*.md`.
Each milestone must end with updated docs, fresh evidence under `docs/evidence/`, and a review against `docs/governance/checklist.md`.

| Milestone | Focus | Detailed plan |
| --- | --- | --- |
| 1 | Identity, password lifecycle, and active sessions | [Milestone 1](milestones/01-identity-sessions.md) |
| 2 | Room catalog, membership, and moderation | [Milestone 2](milestones/02-rooms-moderation.md) |
| 3 | Friendships, blocks, and direct-dialog eligibility | [Milestone 3](milestones/03-contacts-direct-dialogs.md) |
| 4 | Realtime messaging, unread markers, and history pagination | [Milestone 4](milestones/04-messaging-history-realtime.md) |
| 5 | Attachments and access revocation | [Milestone 5](milestones/05-attachments-access-control.md) |
| 6 | Presence and multi-tab behavior | [Milestone 6](milestones/06-presence-multi-tab.md) |
| 7 | Jabber/XMPP client support and inter-server federation | [Milestone 7](milestones/07-xmpp-federation.md) |
| 8 | Jabber admin dashboards and federation load validation | [Milestone 8](milestones/08-admin-dashboards-load-validation.md) |

## Sequence Rules
- Milestone 1 establishes the security, persistence, and static-web foundation for every later slice.
- Milestones 2 and 3 must land before Milestone 4 because room membership and direct-dialog eligibility gate messaging.
- Milestone 5 depends on Milestone 4 because attachments are message-bound chat content.
- Milestone 6 depends on Milestone 4 because tab activity and presence fan-out use the WebSocket channel.
- Milestone 7 depends on the B3 XMPP decision note produced during Milestone 1 and on the direct-dialog and messaging rules from Milestones 3 and 4.
- Milestone 8 depends on Milestone 7 for real XMPP and federation state.

## Cross-Milestone Defaults
- Keep one Spring Boot application in `apps/api`; do not add microservices or a separate frontend.
- Use HTTP for writes and queries, raw JSON WebSocket over `/ws` for push and tab activity, and Flyway plus PostgreSQL for durable state.
- Add Spring Security, Flyway, Bean Validation, Jackson, and Testcontainers in Milestone 1 and reuse them throughout.
- Keep authentication same-origin and cookie-based with `CHAT_SESSION` as the default cookie name.
- Use a JS-readable `XSRF-TOKEN` cookie and `X-CSRF-TOKEN` header for state-changing browser requests.
- Keep UI assets under `apps/api/src/main/resources/static` with vanilla JavaScript modules and no build tool.
- Add only forward Flyway migrations; never rewrite committed migrations.
- End every milestone with a green `./gradlew test`, updated docs, and a dated evidence note.

## Milestone Review Output
Every milestone review must capture:
- requirement slice covered
- APIs or domain rules added or changed
- evidence produced and where it lives
- bets resolved, narrowed, or newly opened
- next milestone and its blocking assumptions
