# Milestone 4: Realtime Messaging, Unread Markers, And History Pagination

## Summary
This milestone turns the app into a usable chat system.
It delivers room and direct-dialog messaging, history reads, replies, unread markers, and the raw JSON WebSocket push channel.

## Deliverables
- Message send, edit, delete, reply, unread markers, initial history load, and older-history pagination for both rooms and direct dialogs.
- Raw JSON WebSocket endpoint at `/ws` for message, unread, moderation, and later presence fan-out.
- First real chat timeline UI and composer.
- Session-revocation handling for live WebSocket sessions.

## Out Of Scope
- Attachments.
- Presence and tab activity.
- XMPP interoperability.

## Implementation Plan
1. Messaging feature module
   - Implement `api`, `application`, and `spi` packages in `modules/features/messaging`.
   - Build all core logic around `ChatTargetRef`.
   - Add feature services for send, edit, delete, history page read, and read-marker advance.
2. Database and persistence adapter
   - Add Flyway migrations for `messages` and `chat_unread_markers`.
   - Implement JPA entities and repositories in `modules/adapters/persistence-jpa/messaging`.
   - Enforce the rule that a message references exactly one target: room or direct dialog.
3. Domain rules
   - Room sends require current room membership and absence from the ban list.
   - Direct-dialog sends require active eligibility from the contacts feature.
   - Message edits are author-only.
   - Message deletes are author-only in direct dialogs and author-or-moderator in room chats.
   - Deletes are state transitions to `MessageState.DELETED`; do not physically remove message rows.
   - Replies store an optional parent message reference and must not cross chat boundaries.
4. History and unread semantics
   - `GET /api/chats/{chatType}/{chatId}/messages` returns messages in chronological order within a page.
   - `before` is the oldest message already visible, not an offset.
   - Initial load returns the newest page.
   - Read markers advance only forward and drive unread badge clearing.
5. WebSocket channel
   - Implement raw JSON `/ws` using Spring WebSocket handlers in `apps/api/src/main/java/edu/artemiy/chat/app/websocket`.
   - Authenticate the handshake with the same session cookie used by HTTP.
   - Use the canonical event envelope from `docs/api-contracts.md`.
   - Deliver `message.created`, `message.updated`, `message.deleted`, and `unread.updated` in this milestone.
   - Prepare the same channel to carry moderation and presence events later.
6. Session revocation integration
   - Once `/ws` exists, revoking a session must terminate or invalidate any live socket tied to that session id.
   - Reuse the Milestone 1 auth store as the source of truth.
7. UI wiring
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
- The UI must show edited and deleted markers distinctly.

## Tests And Evidence
- Automated
  - service tests for room and direct-dialog send eligibility, edit and delete permissions, reply linkage, unread updates, and tombstoned author rendering
  - persistence tests for cursor pagination ordering
  - HTTP integration tests for chronological page order and read-marker semantics
  - WebSocket integration tests for `message.created`, `message.updated`, `message.deleted`, and `unread.updated`
  - session-revocation test proving a live WebSocket stops receiving events after its backing session is revoked
- Manual
  - UI proof for room and direct-dialog messaging, reply, edit, delete, and unread clearing
  - reconnect note showing offline messages appear after reconnect
  - timing note showing local delivery remains under the target

## Exit Criteria
- The app supports real chat in rooms and direct dialogs.
- History and unread semantics match ADR 0003.
- The milestone ends with a green `./gradlew test` and a dated evidence note in `docs/evidence/`.
