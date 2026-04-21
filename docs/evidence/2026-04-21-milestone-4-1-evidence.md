# Milestone 4.1 Evidence

- Date: 2026-04-21
- Slice: Milestone 4.1 messaging module foundation, messages persistence, unread-marker persistence, HTTP send or history endpoints, and forward-only read markers

## Scope Covered

- Messaging feature module foundation under `modules/features/messaging`
- PostgreSQL and Flyway persistence for `messages` and `chat_unread_markers`
- HTTP endpoints:
  - `POST /api/chats/{chatType}/{chatId}/messages`
  - `GET /api/chats/{chatType}/{chatId}/messages`
  - `POST /api/chats/{chatType}/{chatId}/read-markers`
- Room and direct-dialog authorization rules for the 4.1 send and history slice

## Commands Run

```bash
./gradlew compileJava testClasses
./gradlew :modules:features:messaging:test --tests 'edu.artemiy.chat.messaging.application.DefaultMessagingServiceTests' :modules:adapters:persistence-jpa:test --tests 'edu.artemiy.chat.adapters.persistence.jpa.messaging.MessagingRepositoryIntegrationTests' :apps:api:test --tests 'edu.artemiy.chat.app.http.MessagingHttpIntegrationTests'
./gradlew test
APP_PORT=18080 docker compose up -d --build db app
```

Live API proof used authenticated `curl` requests against the compose app on `http://localhost:18080` for:

1. room message send through `POST /api/chats/room/{roomId}/messages`
2. room newest-page history through `GET /api/chats/room/{roomId}/messages?limit=2`
3. older room page through `GET /api/chats/room/{roomId}/messages?limit=2&before={oldestVisibleMessageId}`
4. read-marker advance through `POST /api/chats/room/{roomId}/read-markers`
5. direct-dialog send through `POST /api/chats/direct/{dialogId}/messages`
6. direct-dialog history after block through `GET /api/chats/direct/{dialogId}/messages`
7. denied direct-dialog send after block through `POST /api/chats/direct/{dialogId}/messages`

## Results

### Automated

- `DefaultMessagingServiceTests` passed with coverage for:
  - room send eligibility
  - direct-dialog send eligibility
  - room history authorization
  - direct-dialog history authorization
  - newest-page loading
  - `before` cursor loading
  - chronological page order
  - forward-only read markers
- `MessagingRepositoryIntegrationTests` passed with coverage for:
  - exclusive room-or-direct target enforcement on `messages`
  - room history ordering plus `idx_messages_room_history` evidence
  - direct-dialog history ordering plus `idx_messages_direct_dialog_history` evidence
  - unread-marker insert or update behavior
- `MessagingHttpIntegrationTests` passed with coverage for:
  - room message send
  - room history read and `before` cursor behavior
  - read-marker forward-only behavior
  - room authorization failures
  - direct-dialog history readability after block and send denial while blocked
- `./gradlew test` passed for the full repository.

### Live API Proof

- Room history newest page returned `["Two", "Three"]`.
- Room older page returned `["One"]` when called with `before` equal to the oldest message from the newest page.
- Advancing the read marker to the newest room message succeeded.
- Reposting the older room message id did not move the marker backward; the returned `lastReadMessageId` stayed on the newer message.
- Direct-dialog history after a block still returned `["Still there"]`.
- A new direct-dialog send after the block returned HTTP `403` with code `messaging.direct_dialog_blocked`.

## Root-Cause Note

- During verification, the new messaging repository test initially failed intermittently because the test fixture ordered direct-dialog participant UUIDs with Java `UUID.compareTo`, while the schema and production code use unsigned UUID ordering. The fix was limited to the test helper so it matches the production ordering rule.

## Assumption And Alternative

- Assumption: Milestone 4.1 history reads default `limit` to `50` and cap it at `100` to keep the backend contract stable before the real timeline UI lands.
- Alternative: require the client to always supply an explicit page size and reject missing `limit` values. This would reduce server-side convention but would add friction to the placeholder and later timeline clients.

## Deferred Beyond 4.1

- reply
- edit
- delete
- `/ws`
- session-revocation handling for live sockets
- replacing the placeholder room or direct-dialog pages with the real timeline
- attachments
- presence

## Bet Impact

- The earlier messaging-history contract bet remains aligned with ADR 0003.
- The history page-size assumption is now recorded in `docs/bet-register.md` as an open question for the later timeline slice.
