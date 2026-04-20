# MVP Delivery Plan

This sequence is the architected implementation order for the required chat scope, including the mandatory advanced Jabber/XMPP and federation requirements.
Each milestone must end with updated docs, fresh evidence under `docs/evidence/`, and an architecture review against `docs/governance/checklist.md`.

## Milestone 1: Identity, Password Lifecycle, And Active Sessions
- Deliver registration, login, logout, password reset, password change, persistent sessions, and active-session listing and revocation.
- Finalize session-cookie security configuration, password hashing, session persistence, and account deletion preconditions.
- Evidence required: automated tests for auth flows, manual proof that revoking one session keeps another active, and an explicit decision update for account deletion behavior outside owned rooms.

## Milestone 2: Room Catalog, Membership, And Moderation
- Deliver room creation, public catalog, private room invites, join and leave rules, ownership, admin management, member removal, bans, unbans, and room deletion.
- Finalize room authorization boundaries and moderation audit record shape.
- Evidence required: tests for public and private room flows, owner-only delete behavior, admin removal-as-ban behavior, and manual UI notes or screenshots for moderation actions.

## Milestone 3: Friendships, Blocks, And Direct-Dialog Eligibility
- Deliver contact list, friend requests, accept and reject actions, friendship removal, user blocks, and direct-dialog creation and lookup.
- Finalize the rules that connect friendship and block state to direct-message eligibility.
- Evidence required: tests for request lifecycle, block terminating friendship, block freezing new DMs, and continued visibility of prior dialog history.

## Milestone 4: Realtime Messaging, Unread Markers, And History Pagination
- Deliver message send, edit, delete, reply, unread markers, initial history load, and older-history pagination.
- Finalize the WebSocket event envelope, fan-out timing, and cursor-based history semantics.
- Evidence required: end-to-end tests for send, edit, delete, unread clearing, chronological pagination, and timing notes that show message delivery under the target.

## Milestone 5: Attachments And Access Revocation
- Deliver attachment upload, metadata display, download, size enforcement, paste or upload entry points, and access revocation tied to room membership or ban changes.
- Finalize storage path conventions, metadata validation, and delete cascades for owned rooms.
- Evidence required: tests for file and image size caps, download authorization, loss of access after room removal or ban, and manual evidence of upload and download flows.

## Milestone 6: Presence And Multi-Tab Behavior
- Deliver per-tab activity tracking, `ONLINE` and `AFK` transitions, `OFFLINE` detection, and presence fan-out to relevant users.
- Finalize heartbeat timing, tab timeout thresholds, and the proof point for staying off Redis in the first iteration.
- Evidence required: tests or scripted probes for multi-tab transitions, logs showing presence propagation under two seconds, and a benchmark note covering the no-Redis bet.

## Milestone 7: Jabber/XMPP Client Support And Inter-Server Federation
- Deliver the chosen Jabber/XMPP interoperability level, mandatory client connectivity, and bidirectional messaging between at least two independently configured servers.
- Finalize the Java-compatible XMPP library choice, federation topology, and the compose-based two-server validation shape.
- Evidence required: a documented library decision, successful Jabber/XMPP client connectivity, and federation message exchange between server A and server B.

## Milestone 8: Jabber Admin Dashboards And Federation Load Validation
- Deliver web UI screens for the admin connection dashboard and federation traffic statistics.
- Run the strongest required validation target: at least 50 connected clients on server A, at least 50 connected clients on server B, and bidirectional messaging between the servers.
- Evidence required: dashboard screenshots, traffic statistics artifacts, load-test notes or logs, and a clear statement of any protocol-level limits that remain.

## Milestone Review Output
Every milestone review must capture:
- requirement slice covered
- APIs or domain rules added or changed
- evidence produced and where it lives
- bets resolved, narrowed, or newly opened
- next milestone and its blocking assumptions
