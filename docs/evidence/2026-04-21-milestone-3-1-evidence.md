# Milestone 3.1 Evidence

Date: 2026-04-21

## Requirement Slice
- Friendship request creation by username or user id
- Friendship request acceptance
- Friendship request rejection
- Contacts list baseline with accepted friends, inbound pending requests, outbound pending requests
- Opposite-direction pending request auto-accept
- Dedicated contacts-focused static page linked from the authenticated shell

## Code And Doc Areas Validated
- `modules/features/contacts`
- `modules/adapters/persistence-jpa/.../contacts`
- `apps/api/src/main/java/edu/artemiy/chat/app/http/ContactsHttpController.java`
- `apps/api/src/main/resources/static/contacts.html`
- `apps/api/src/main/resources/static/js/contacts-page.js`
- Milestone 3.1 doc and contract updates in `docs/` plus `README.md`

## Automated Validation

Commands run:
- `./gradlew :modules:features:contacts:test :modules:adapters:persistence-jpa:test :apps:api:test --tests '*Contacts*'`
- `./gradlew test`

Actual results:
- The targeted contacts test command passed after adding the feature, persistence, and HTTP integration coverage for the Milestone 3.1 slice.
- The full `./gradlew test` run passed on the final tree.

Coverage exercised by the targeted contacts suite:
- service tests for request creation by username and user id, duplicate pending prevention, reverse-direction auto-accept, explicit accept, explicit reject, and contacts list state
- persistence tests for friendship pair uniqueness, pending-request uniqueness, and request state transitions
- HTTP integration tests for username and user-id request creation, accept, reject, reverse-direction auto-accept, and `GET /api/contacts`

## Browser Proof

Runtime setup commands:
- `POSTGRES_PORT=55432 docker compose up -d db`
- `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/chat SPRING_DATASOURCE_USERNAME=chat SPRING_DATASOURCE_PASSWORD=chat SERVER_PORT=8080 ./gradlew :apps:api:bootRun`
- `docker exec chat-db-1 psql -U chat -d chat -c "truncate table friendships, friendship_requests, moderation_audit_events, room_bans, room_invites, room_memberships, rooms, user_sessions, password_reset_tokens, users cascade;"`

Browser automation command:
- Executed a temporary Python Playwright script from `/tmp/chat-proof-venv` against the local Chrome binary to register four users, drive the contacts page flows, and save screenshots under `docs/evidence/`.

Saved screenshots:
- Create friend request by username: [2026-04-21-milestone-3-1-create-request.png](2026-04-21-milestone-3-1-create-request.png)
- Accept request: [2026-04-21-milestone-3-1-accept-request.png](2026-04-21-milestone-3-1-accept-request.png)
- Reject request: [2026-04-21-milestone-3-1-reject-request.png](2026-04-21-milestone-3-1-reject-request.png)
- Reverse-direction auto-accept: [2026-04-21-milestone-3-1-auto-accept.png](2026-04-21-milestone-3-1-auto-accept.png)
- Contacts page with accepted plus pending sections: [2026-04-21-milestone-3-1-contacts-overview.png](2026-04-21-milestone-3-1-contacts-overview.png)

Actual browser results:
- The dedicated contacts page loaded from `/app/contacts` for authenticated users.
- A request created by username appeared in outbound pending state.
- An inbound request could be accepted from the same page and moved into the accepted-friends section.
- An inbound request could be rejected from the same page and disappeared from pending state.
- Sending a request back to a user who already had a pending inbound request auto-accepted the existing request and moved the pair into accepted friends.
- The contacts page rendered accepted friends, inbound pending requests, and outbound pending requests together without block or direct-dialog UI.

## Scope Check
- Implemented in this slice:
  - `GET /api/contacts`
  - `POST /api/friend-requests`
  - `POST /api/friend-requests/{requestId}/accept`
  - `POST /api/friend-requests/{requestId}/reject`
  - `friendship_requests` and `friendships` schema and persistence
  - dedicated contacts page linked from `/app`
- Still deferred:
  - remove friend
  - block and unblock
  - contacts-side account deletion cleanup
  - direct-dialog creation or lookup

## Bets And Follow-Up
- No architecture bet was resolved by this slice.
- The next Milestone 3 work remains the deferred remove-friend, block or unblock, cleanup, and direct-dialog eligibility slices.

## Remaining Risk
- Browser proof used a temporary shell-level Playwright runner because the Playwright MCP wrapper in this environment failed to create `/.playwright-mcp`; the actual application UI was still exercised in a real browser.
- The slice has transaction-level protection and automated behavior coverage, but it does not yet include higher-concurrency or load evidence beyond the normal test suite.
