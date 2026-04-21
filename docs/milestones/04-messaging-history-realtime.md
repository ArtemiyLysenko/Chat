# Milestone 4: Realtime Messaging, Unread Markers, And History Pagination

## Summary
Milestone 4 turns the app into a usable chat system.
It is intentionally split into narrow slices so the HTTP and persistence foundation can land before live push and WebSocket-specific behavior.

Milestone 4.1, Milestone 4.2, and the backend slice of Milestone 4.3 are now implemented.
The milestone is not closed yet because Milestone 4.4 still needs to wire the browser UI to the live `/ws` channel and complete reconnect behavior.

## Delivered Slices
- Milestone 4.1 foundation
  - messaging feature module with `api`, `application`, and `spi` packages
  - shared messaging types `ChatTargetRef`, `MessageState`, and `UnreadMarker`
  - `messages` and `chat_unread_markers` persistence plus Flyway migrations
  - `POST /api/chats/{chatType}/{chatId}/messages`
  - `GET /api/chats/{chatType}/{chatId}/messages`
  - `POST /api/chats/{chatType}/{chatId}/read-markers`
  - newest-page history load, older-page loading by `before`, and forward-only read markers
- Milestone 4.2 HTTP chat workflow
  - reply-capable message send with same-chat reply validation
  - message edit and logical delete over HTTP
  - first real room and direct-dialog timeline UI
  - HTTP-backed unread badge rendering in room and direct-dialog navigation
  - reply target UI, composer UI, edited or deleted markers, and tombstoned-author rendering
- Milestone 4.3 backend realtime fan-out
  - raw authenticated `/ws` endpoint using the same session cookie as HTTP
  - canonical server event envelope from `docs/api-contracts.md`
  - backend fan-out for `message.created`, `message.updated`, `message.deleted`, and `unread.updated`
  - live socket invalidation on session revocation through `session.revoked`
  - WebSocket integration coverage for message fan-out, unread updates, handshake auth, and live session revocation

## Remaining Milestone 4 Scope
- Milestone 4.4 only
  - wire the room and direct-dialog browser UI to live `/ws` events
  - add reconnect behavior and HTTP refresh after reconnect where needed
  - keep session-revocation behavior visible to live browser tabs
  - close full Milestone 4 and update the repo status docs

## Out Of Scope
- Attachments
- Presence and tab activity
- XMPP interoperability
- Federation
- For Milestone 4.3 specifically: browser-side reconnect polish, live room or direct-dialog UI updates, and the Milestone 4 closeout docs

## Milestone 4.2 Locks
- Keep one reusable messaging domain path for room and direct-dialog rules inside `modules/features/messaging`.
- Keep HTTP as the only write path for send, edit, delete, and read-marker updates.
- Use the Milestone 4.1 history endpoint for newest-page load and older-page fetches by `before`.
- Reply targets must stay inside the same room or direct dialog as the new message.
- The current 4.2 implementation allows replies to deleted messages as long as the reply target stays in the same chat.
- Edit is author-only and preserves the original message row.
- Delete is logical delete only:
  - direct dialogs: author-only
  - rooms: author or room `OWNER` or `ADMIN`
- Tombstoned authors must render through the preserved `users` row rather than through broken references.
- Uploads stay disabled until Milestone 5.

## Implementation Shape
1. Milestone 4.1 foundation
   - Keep shared message and unread types independent from transport details.
   - Persist chat history and unread markers in PostgreSQL through the JPA adapter.
2. Milestone 4.2 message lifecycle
   - Extend send to support replies through `parentMessageId`.
   - Add HTTP edit and delete mutations without introducing row removal.
   - Preserve tombstoned-author joins in both history and reply-preview rendering.
3. Milestone 4.2 UI wiring
   - Replace placeholder room and direct-dialog panels with HTTP-driven timelines and composers.
   - Render unread badges from `/api/rooms?scope=joined` and `/api/contacts`.
   - Keep paging simple: initial newest page plus explicit older-history loading by `before`.
4. Milestone 4.3 backend
   - Add `/ws` push only after the HTTP lifecycle and UI remain stable.
   - Reuse the Milestone 1 auth store as the source of truth for live-session validity.
5. Milestone 4.4 UI closeout later
   - Reconnect through the authenticated `/ws` channel and refresh HTTP-backed room or contact state after reconnect.
   - Keep the HTTP mutation path as the only write path while the browser starts consuming live server events.

## Tests And Evidence
- Automated
  - Milestone 4.1 and 4.2 service tests for send eligibility, same-chat reply rules, edit or delete authorization, tombstoned-author rendering, history authorization, paging, and forward-only read markers
  - Milestone 4.1 and 4.2 persistence tests for target exclusivity, reply linkage, edited or deleted state persistence, history ordering, and unread-marker persistence
  - Milestone 4.1 and 4.2 HTTP integration tests for reply-capable send, edit, delete, room moderator delete, direct-dialog delete authorization, history reads, and unread-marker or unread-count flows
  - Milestone 4.3: WebSocket integration tests for `message.created`, `message.updated`, `message.deleted`, `unread.updated`, handshake auth, and live session revocation
- Manual
  - Milestone 4.2 browser proof for room timeline render, direct-dialog timeline render, send, reply, edit, delete, tombstoned-author render, and unread badge clear-through-read behavior
  - Milestone 4.4 later: reconnect and live fan-out proof

## Exit Criteria
- Milestone 4.1 ends with the HTTP and persistence foundation in place for rooms and direct dialogs.
- Milestone 4.2 ends with HTTP-driven room and direct-dialog chat screens, reply or edit or delete behavior, and unread badges without claiming live push.
- Milestone 4.3 is the only backend slice that introduces `/ws` or live session-revocation handling.
- Milestone 4.4 closes the remaining browser live-update behavior and the milestone status docs.
- Each completed slice ends with a green `./gradlew test` and a dated evidence note in `docs/evidence/`.
