# Milestone 3.2 Evidence

Date: 2026-04-21

## Requirement Slice
- Remove friend
- Block user
- Unblock user
- Centralized direct-message eligibility rules
- Dedicated contacts page updates for accepted friends, pending requests, blocked users, remove-friend controls, and block or unblock controls

## Code And Doc Areas Validated
- `modules/features/contacts`
- `modules/adapters/persistence-jpa/.../contacts`
- `apps/api/src/main/java/edu/artemiy/chat/app/http/ContactsHttpController.java`
- `apps/api/src/main/resources/static/contacts.html`
- `apps/api/src/main/resources/static/js/contacts-page.js`
- Milestone 3.2 contract, persistence, architecture, milestone, README, and evidence index updates under `docs/`

## Automated Validation

Commands run:
- `./gradlew :modules:features:contacts:test --tests 'edu.artemiy.chat.contacts.application.DefaultContactsServiceTests' :modules:adapters:persistence-jpa:test --tests 'edu.artemiy.chat.adapters.persistence.jpa.contacts.ContactsRepositoryIntegrationTests' :apps:api:test --tests 'edu.artemiy.chat.app.http.ContactsHttpIntegrationTests'`
- `./gradlew test`

Actual results:
- The targeted contacts suites passed with Milestone 3.2 coverage for remove friend, block or unblock, blocked-request denial, and centralized direct-message eligibility.
- The final full `./gradlew test` run passed on the completed Milestone 3.2 tree.

Coverage exercised by the automated suite:
- service tests for remove friend, block removing friendship, block without friendship, unblock, blocked friend-request denial, deleted-contact action safety, and direct-message eligibility
- persistence tests for user-block uniqueness, block persistence, friendship deletion support, and friendship-request transition behavior
- HTTP integration tests for remove friend, block, unblock, blocked request denial, and `GET /api/contacts` showing blocked users separately

## Browser Proof

Runtime setup commands:
- `APP_PORT=18080 docker compose up -d --build app`
- `docker compose exec -T db psql -U chat -d chat -c "truncate table user_blocks, friendships, friendship_requests, moderation_audit_events, room_bans, room_invites, room_memberships, rooms, user_sessions, password_reset_tokens, users cascade;"`

Browser automation command:
- `source /tmp/chat-proof-venv/bin/activate && python - <<'PY' ... PY`

Notes:
- The live browser proof used a temporary shell-level Playwright runner because the MCP browser tooling available in this environment can navigate and screenshot but cannot complete the authenticated form and button interactions needed for the Milestone 3.2 contacts flows.
- The Playwright runner registered isolated users against the live app on `http://127.0.0.1:18080`, drove the actual contacts UI interactions, and saved screenshots directly under `docs/evidence/`.

Saved screenshots:
- Remove-friend flow: [2026-04-21-milestone-3-2-remove-friend.png](2026-04-21-milestone-3-2-remove-friend.png)
- Block flow: [2026-04-21-milestone-3-2-block-user.png](2026-04-21-milestone-3-2-block-user.png)
- Blocked-users subsection: [2026-04-21-milestone-3-2-blocked-users-section.png](2026-04-21-milestone-3-2-blocked-users-section.png)
- Unblock flow: [2026-04-21-milestone-3-2-unblock-user.png](2026-04-21-milestone-3-2-unblock-user.png)
- Blocked friend-request denial in UI: [2026-04-21-milestone-3-2-request-denied-while-blocked.png](2026-04-21-milestone-3-2-request-denied-while-blocked.png)

Actual browser results:
- An accepted friend could be removed from the dedicated contacts page and disappeared from the accepted-friends section immediately.
- Blocking an accepted friend moved the pair out of accepted friends and into the dedicated blocked-users subsection.
- The blocked-users subsection rendered the blocked user with an unblock control.
- Unblocking removed the blocked user from the blocked-users subsection and did not restore friendship automatically.
- Attempting to send a new friend request from the contacts page while a block existed showed the expected error message in the UI.

## Scope Check
- Implemented in this slice:
  - `GET /api/contacts` with accepted, pending, and blocked sections
  - `DELETE /api/contacts/{userId}`
  - `PUT /api/blocks/{userId}`
  - `DELETE /api/blocks/{userId}`
  - `user_blocks` schema and persistence
  - centralized friendship plus block based `DirectMessageEligibility`
  - dedicated contacts page actions for remove friend, block, and unblock
- Still deferred:
  - direct-dialog ensure or lookup
  - direct-dialog placeholder UI
  - contacts-side account deletion cleanup
  - full Milestone 3 closeout

## Bets And Follow-Up
- B7 resolved in this slice: blocking now retires any pending friend request between the pair so contacts state stays coherent for later direct-dialog identity work.
- The next Milestone 3 work remains stable direct-dialog identity plus the deferred contacts-side account-deletion cleanup.

## Remaining Risk
- Browser proof now covers the user-visible Milestone 3.2 flows, and the full Gradle test suite is green.
- The remaining unverified area is concurrency behavior under simultaneous block and friend-request races beyond the normal transaction and integration-test coverage.
