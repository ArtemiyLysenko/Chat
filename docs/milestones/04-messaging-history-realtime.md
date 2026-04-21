# Milestone 4: Realtime Messaging, Unread Markers, And History Pagination

## Summary
Milestone 4 turns the app into a usable chat system.
It is now split into narrower delivery slices so the backend, persistence, and contract foundation can land before reply, edit or delete behavior, realtime push, and the real timeline UI.

Milestone 4.1 is the currently implemented slice.
It delivers the messaging feature module foundation, messages persistence, unread-marker persistence, HTTP send and history endpoints, and forward-only read-marker updates for room chats and direct dialogs.

## Deliverables
- Milestone 4.1
  - messaging feature module with `api`, `application`, and `spi` packages
  - shared messaging types `ChatTargetRef`, `MessageState`, and `UnreadMarker`
  - `messages` and `chat_unread_markers` persistence plus Flyway migrations
  - `POST /api/chats/{chatType}/{chatId}/messages`
  - `GET /api/chats/{chatType}/{chatId}/messages`
  - `POST /api/chats/{chatType}/{chatId}/read-markers`
  - newest-page history load, older-page loading by `before`, and forward-only read markers
- Later Milestone 4 slices
  - reply, edit, and delete behavior
  - raw JSON WebSocket endpoint at `/ws`
  - first real chat timeline UI and composer
  - session-revocation handling for live WebSocket sessions

## Out Of Scope
- Attachments.
- Presence and tab activity.
- XMPP interoperability.
- For Milestone 4.1 specifically: reply, edit, delete, `/ws`, session-revocation handling for live sockets, and replacing the placeholder room or direct-dialog pages.

## Implementation Plan
1. Milestone 4.1 foundation
   - Implement `api`, `application`, and `spi` packages in `modules/features/messaging`.
   - Build all current core logic around `ChatTargetRef`.
   - Add feature services for send, history page read, and read-marker advance.
2. Milestone 4.1 database and persistence adapter
   - Add Flyway migrations for `messages` and `chat_unread_markers`.
   - Implement JPA entities and repositories in `modules/adapters/persistence-jpa/messaging`.
   - Enforce the rule that a message references exactly one target: room or direct dialog.
3. Milestone 4.1 domain rules
   - Room sends require current room membership and absence from the ban list.
   - Direct-dialog sends require active eligibility from the contacts feature.
   - Direct-dialog history reads require current dialog participation even after later friendship removal or block.
   - Message edits, message deletes, delete-state transitions, and reply linkage remain deferred.
4. Milestone 4.1 history and unread semantics
   - `GET /api/chats/{chatType}/{chatId}/messages` returns messages in chronological order within a page.
   - `before` is the oldest message already visible, not an offset.
   - Initial load returns the newest page.
   - Read markers advance only forward and drive unread badge clearing.
5. Later Milestone 4 slices
   - Implement raw JSON `/ws` using Spring WebSocket handlers in `apps/api/src/main/java/edu/artemiy/chat/app/websocket`.
   - Authenticate the handshake with the same session cookie used by HTTP.
   - Use the canonical event envelope from `docs/api-contracts.md`.
   - Deliver `message.created`, `message.updated`, `message.deleted`, and `unread.updated` in this milestone.
   - Prepare the same channel to carry moderation and presence events later.
6. Later Milestone 4 session revocation integration
   - Once `/ws` exists, revoking a session must terminate or invalidate any live socket tied to that session id.
   - Reuse the Milestone 1 auth store as the source of truth.
7. Later Milestone 4 UI wiring
   - Replace placeholder room and direct-dialog panels with:
     - message timeline
     - composer
     - reply target UI
     - unread badge rendering in sidebars
   - Keep uploads disabled until Milestone 5.
8. Account deletion integration
   - The messaging module does not delete historical non-owned messages.
   - It must render tombstoned authors cleanly through the preserved `users` row from Milestone 1.

## Contract And Behavior Locks
- Use the messaging, history, and read-marker endpoints already defined in `docs/api-contracts.md`.
- Use `ChatTargetRef`, `MessageState`, and `UnreadMarker` exactly as the shared contract types.
- Keep HTTP as the only write path for message mutations and history reads.
- Keep WebSocket as the push channel only; do not introduce STOMP or move writes to WebSocket.
- Milestone 4.1 does not implement reply, edit, delete, or realtime push behavior yet.
- The UI must show edited and deleted markers distinctly once those later slices land.

## Tests And Evidence
- Automated
  - Milestone 4.1 service tests for room and direct-dialog send eligibility, room and direct-dialog history authorization, newest-page reads, `before` cursor reads, chronological order, and forward-only read markers
  - Milestone 4.1 persistence tests for target exclusivity, room and direct-dialog history ordering plus index coverage where practical, and unread-marker persistence or update behavior
  - Milestone 4.1 HTTP integration tests for message send, history reads, read-marker semantics, room authorization failures, and direct-dialog authorization behavior
  - later Milestone 4 WebSocket integration tests for `message.created`, `message.updated`, `message.deleted`, and `unread.updated`
  - later Milestone 4 session-revocation test proving a live WebSocket stops receiving events after its backing session is revoked
- Manual
  - Milestone 4.1 API proof for room and direct-dialog message send and history reads, older-page loading by `before`, and read-marker advance behavior
  - later Milestone 4 UI proof for reply, edit, delete, unread clearing, and realtime behavior
  - later reconnect note showing offline messages appear after reconnect

## Exit Criteria
- Milestone 4.1 ends with the HTTP and persistence foundation in place for rooms and direct dialogs without leaking 4.2 or 4.3 behavior.
- History and unread semantics match ADR 0003 for the currently implemented send, read, and read-marker scope.
- The current slice ends with a green `./gradlew test` and a dated evidence note in `docs/evidence/`.
