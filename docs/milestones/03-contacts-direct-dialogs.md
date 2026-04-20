# Milestone 3: Friendships, Blocks, And Direct-Dialog Eligibility

## Summary
This milestone delivers contacts management, friend request workflow, block behavior, and stable direct-dialog creation.
Direct-dialog existence is established here, but actual message exchange remains deferred to Milestone 4.

## Deliverables
- Contacts list with pending, accepted, and blocked states.
- Friend request create, accept, reject, remove-friend, block, unblock, and direct-dialog create or fetch flows.
- Direct-message eligibility rules captured as executable domain logic.
- Contacts-side account deletion cleanup hooks.

## Out Of Scope
- Actual direct-message send or history rendering.
- Realtime contacts updates.
- Presence indicators.

## Implementation Plan
1. Contacts feature module
   - Implement `api`, `application`, and `spi` packages in `modules/features/contacts`.
   - Add feature services for request creation, acceptance, rejection, friend removal, block, unblock, and direct-dialog ensure or lookup.
   - Keep `DirectMessageEligibility` as the shared decision type used later by messaging and XMPP.
2. Database and persistence adapter
   - Add Flyway migrations for `friendship_requests`, `friendships`, `user_blocks`, and `direct_dialogs`.
   - Implement JPA entities and repositories in `modules/adapters/persistence-jpa/contacts`.
   - Enforce uniqueness for friendship pairs, direct-dialog pairs, and blocker-blocked pairs.
3. Domain rules
   - Support friend request creation by username or by user id.
   - Reject duplicate pending friend requests in the same direction.
   - Accepting a request creates the friendship relation and makes the pair eligible for direct messaging.
   - Removing a friend preserves direct-dialog identity but makes the pair ineligible for new direct messages.
   - Blocking immediately removes the friendship relation, prevents new friend requests, and makes the pair ineligible for new direct messages.
   - Unblocking does not restore the old friendship automatically.
4. HTTP and UI wiring
   - Implement contacts controllers in `apps/api/src/main/java/edu/artemiy/chat/app/http`.
   - Add static UI for:
     - contacts list
     - pending inbound and outbound requests
     - block management
     - direct-dialog placeholder view
   - The direct-dialog page may exist as an empty timeline shell until Milestone 4.
5. Account deletion integration
   - Implement `AccountDeletionImpactPort` from the contacts side.
   - Cleanup must remove or invalidate:
     - pending friend requests created by or targeting the deleted user
     - friendship rows involving the deleted user
     - block rows involving the deleted user
   - Direct-dialog rows may remain if later message history needs the stable dialog identifier.

## Contract And Behavior Locks
- Use the contacts and direct-dialog endpoints already defined in `docs/api-contracts.md`.
- `POST /api/friend-requests` accepts either `userId` or `username`, plus optional `messageText`.
- `POST /api/direct-dialogs/{userId}` must either create the stable dialog or return the existing one for that user pair.
- The contacts list response must be sufficient for the classic contacts view: accepted friends, pending requests, and block state.
- Existing direct-dialog identity remains visible after friend removal or block, but sending is still denied while the pair is ineligible.

## Tests And Evidence
- Automated
  - service tests for request lifecycle, duplicate prevention, accept and reject, friendship removal, block terminating friendship, unblock not restoring friendship, and direct-dialog creation only when eligible
  - persistence tests for ordered-pair uniqueness
  - HTTP integration tests for username-based request creation and direct-dialog ensure or fetch
- Manual
  - UI proof for send request, accept request, remove friend, block, and unblock
  - note showing a direct-dialog placeholder can be opened but not yet used for messaging

## Exit Criteria
- The direct-dialog identifier is stable and ready for Milestone 4.
- Block and friendship rules are encoded in one reusable domain path rather than repeated in controllers.
- The milestone ends with a green `./gradlew test` and a dated evidence note in `docs/evidence/`.
