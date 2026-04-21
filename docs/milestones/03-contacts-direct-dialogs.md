# Milestone 3: Friendships, Blocks, And Direct-Dialog Eligibility

## Summary
Milestone 3 is being delivered in slices so contacts behavior can land without prematurely dragging in blocks or direct dialogs.
Milestone 3.1 delivers friendship request workflow, accepted-friends state, and a dedicated contacts page.
Milestone 3.2 adds friendship removal, block and unblock behavior, and the shared direct-message eligibility rule.
Later Milestone 3 slices still need to add contacts-side account-deletion cleanup and stable direct-dialog identity.

## Milestone 3.1 Deliverables
- Contacts feature `api`, `application`, and `spi` packages with one central friendship-request path.
- Friend request creation by username or by user id, plus explicit accept and reject flows.
- Opposite-direction pending request auto-accept wired through `POST /api/friend-requests`.
- Contacts list baseline with accepted friends, inbound pending requests, outbound pending requests, and a forward-compatible empty blocked section.
- Dedicated contacts-focused static page linked from the main authenticated shell.
- Forward Flyway migrations and JPA persistence for `friendship_requests` and `friendships` only.

## Milestone 3.1 Out Of Scope
- Remove friend.
- Block and unblock behavior.
- Direct-dialog creation or lookup.
- Contacts-side account deletion cleanup.
- Realtime contacts updates.
- Presence indicators.
- Actual direct-message send or history rendering.

## Milestone 3 Slice Plan
1. Milestone 3.1
   - Implement the friend-request create, accept, reject, and contacts-list baseline.
   - Add `friendship_requests` and `friendships` persistence only.
   - Ship the dedicated contacts page linked from `/app`.
2. Later Milestone 3 slice
   - Add remove-friend plus block and unblock behavior.
   - Add contacts-side account-deletion cleanup.
3. Later Milestone 3 slice
   - Add stable direct-dialog ensure or lookup and the rest of the direct-message eligibility workflow needed before Milestone 4 messaging.

## Milestone 3.2 Deliverables
- Contacts feature `api`, `application`, `domain`, and `spi` packages extended for remove-friend, block, unblock, and reusable direct-message eligibility evaluation.
- One reusable contacts-domain relationship path that evaluates friendship plus directional block state and feeds both friend-request gating and `DirectMessageEligibility`.
- Forward Flyway migration and JPA persistence for `user_blocks`, including blocker-blocked uniqueness.
- `DELETE /api/contacts/{userId}`, `PUT /api/blocks/{userId}`, and `DELETE /api/blocks/{userId}` wired into the existing contacts HTTP surface.
- Dedicated contacts page updated with accepted friends, pending requests, a real blocked-users subsection, remove-friend controls, and block or unblock controls.

## Milestone 3.2 Behavior Locks
- Removing a friend deletes the friendship relation only. It does not create a block and does not create a direct dialog.
- Blocking a user works with or without an existing friendship.
- Blocking immediately removes any active friendship and retires any pending friendship request between the pair so the contacts state stays coherent for later stable direct-dialog identity.
- New friend requests are denied while a block exists in either direction.
- Unblocking removes only the caller's directional block and does not restore friendship automatically.
- Direct-message eligibility is centralized and currently depends on:
  - active friendship
  - no block in either direction

## Milestone 3.2 Out Of Scope
- Direct-dialog ensure or lookup.
- Direct-dialog placeholder UI.
- Contacts-side account deletion cleanup.
- Realtime contacts updates.
- Presence indicators.
- Actual direct-message send or history rendering.

## Milestone 3.1 Contract And Behavior Locks
- Use `GET /api/contacts`, `POST /api/friend-requests`, `POST /api/friend-requests/{requestId}/accept`, and `POST /api/friend-requests/{requestId}/reject`.
- `POST /api/friend-requests` accepts optional `messageText` plus exactly one of `userId` or `username`.
- If a pending reverse-direction request already exists, the same create endpoint auto-converts into accepting that existing request.
- The contacts list response must already support the classic contacts view: accepted friends, inbound pending requests, outbound pending requests, and a forward-compatible `blockedUsers` section.
- Keep `DirectMessageEligibility` as the shared type for later slices, but do not implement direct-dialog behavior in Milestone 3.1.

## Milestone 3.1 Tests And Evidence
- Automated
  - service tests for request creation by username and user id, duplicate pending prevention, reverse-direction auto-accept, explicit accept, explicit reject, and contacts list state
  - persistence tests for friendship pair uniqueness, pending-request uniqueness, and request transition behavior
  - HTTP integration tests for request creation by username and user id, accept, reject, reverse-direction auto-accept, and `GET /api/contacts`
- Manual
  - browser proof for creating a request by username
  - browser proof for accepting and rejecting requests
  - browser proof for reverse-direction auto-accept
  - browser proof for accepted and pending sections on the dedicated contacts page

## Milestone 3.1 Exit Criteria
- Friendship request rules are encoded in one reusable contacts-domain path rather than repeated in controllers or repositories.
- The dedicated contacts page is reachable from the main authenticated shell and renders accepted plus pending relationship state.
- Only Milestone 3.1 scope is implemented; blocks, remove-friend, and direct-dialog work remain deferred.
- The slice ends with a green `./gradlew test` and a dated Milestone 3.1 evidence note in `docs/evidence/`.

## Milestone 3.2 Tests And Evidence
- Automated
  - service tests for remove friend, block and unblock, friend-request denial while blocked, and direct-message eligibility
  - persistence tests for user-block uniqueness, block persistence, and friendship removal support
  - HTTP integration tests for remove friend, block, unblock, blocked request denial, and `GET /api/contacts` with blocked users separated from accepted and pending state
- Manual
  - browser proof for remove friend flow
  - browser proof for block flow
  - browser proof for unblock flow
  - browser proof for the blocked-users subsection on the dedicated contacts page

## Milestone 3.2 Exit Criteria
- Friendship, block, and direct-message eligibility rules are encoded in one reusable contacts-domain path rather than repeated in controllers or repositories.
- The dedicated contacts page renders accepted friends, inbound pending requests, outbound pending requests, and blocked users with the required remove, block, and unblock controls.
- Only Milestone 3.2 scope is implemented; direct-dialog creation, contacts-side account-deletion cleanup, and later messaging work remain deferred.
- The slice ends with a green `./gradlew test` and a dated Milestone 3.2 evidence note in `docs/evidence/`.
