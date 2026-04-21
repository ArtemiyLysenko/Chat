# Milestone 2: Room Catalog, Membership, And Moderation

## Summary
This milestone delivers room lifecycle, public and private room discovery rules, membership management, moderation actions, and the first room-centric UI views.
Messaging is still deferred, so room pages may use a placeholder message panel until Milestone 4 lands.

## Deliverables
- Room creation, public catalog, private rooms, invitations, join, leave, delete, admin grant and revoke, member removal, ban, unban, and ban inspection.
- Classic room list and room-details UI with moderation controls and a placeholder message panel.
- Moderation audit persistence and room authorization boundaries.
- Rooms-side account deletion cleanup hooks.

## Out Of Scope
- Realtime room events and live message delivery.
- Attachment upload or download.
- Room history rendering beyond a placeholder panel.

## Implementation Plan
1. Rooms feature module
   - Implement `api`, `application`, and `spi` packages in `modules/features/rooms`.
   - Add feature services for room creation, catalog listing, room-detail read, invite, join, leave, delete, role management, membership removal, ban, and unban.
   - Keep `MembershipRole` as the public room-role type.
2. Database and persistence adapter
   - Add Flyway migrations for `rooms`, `room_memberships`, `room_invites`, `room_bans`, and `moderation_audit_events`.
   - Implement JPA entities and repositories in `modules/adapters/persistence-jpa/rooms`.
   - Enforce unique room names in the database.
3. Authorization and visibility rules
   - Public rooms appear in the catalog unless the caller is banned.
   - Private rooms never appear in the public catalog and their details must not leak to non-members and non-invitees.
   - Banned users must not be able to join or view room details.
   - Owners cannot leave their own rooms and must delete them instead.
   - Removing an admin from a room is treated as a ban and must be persisted as both a ban and a moderation audit event.
4. Moderation audit
   - Record actor, target, action, reason, and timestamps for room moderation events.
   - The minimum audited actions are member removal, ban, unban, admin grant, admin revoke, message delete placeholder, and room delete.
5. HTTP and UI wiring
   - Implement room and moderation controllers in `apps/api/src/main/java/edu/artemiy/chat/app/http`.
   - Add static pages and JavaScript modules for:
     - room catalog
     - joined-room sidebar
     - room-detail page with members and moderation controls
     - invite and ban management modals or panels
   - The message panel can remain a placeholder that clearly shows messaging is not available until Milestone 4.
6. Account deletion integration
   - Implement `AccountDeletionImpactPort` from the rooms side.
   - When a user is deleted:
     - delete rooms they own
     - remove them from memberships in non-owned rooms
     - delete or mark obsolete invites and bans where appropriate
   - Keep room deletion as a hard delete of room-owned state.

## Contract And Behavior Locks
- Use the room and moderation endpoints already defined in `docs/api-contracts.md`.
- `POST /api/rooms` accepts `name`, `description`, and `visibility`.
- `POST /api/rooms/{roomId}/invites` accepts a target user id.
- Invited users of a private room may load only a pre-join preview containing the room name and room owner.
- Invitation acceptance reuses `POST /api/rooms/{roomId}/join`; there is one membership-entry path.
- Removing a regular member means remove only, not remove plus ban.
- Removing an admin through member removal means remove, create ban, and record moderation.
- `PUT /api/rooms/{roomId}/bans/{userId}` accepts an optional `reason`.
- `GET /api/rooms/{roomId}/bans` returns current ban records with actor and timestamp metadata.
- Account deletion from Milestone 1 now performs concrete rooms cleanup: owned rooms are deleted, non-owned memberships are removed, invites created by or for the deleted user are removed, and bans targeting the deleted user are cleared.
- `DELETE /api/rooms/{roomId}` records `ROOM_DELETED` inside the delete transaction, and the later hard delete removes all room-owned moderation records with the room.
- All room-read endpoints must hide private-room existence from unauthorized callers.

## Tests And Evidence
- Automated
  - service tests for create, join, leave, invite, private-room visibility, owner leave denial, admin grant and revoke, remove-member-as-ban, unban, and delete-room cascade
  - persistence tests for uniqueness and membership or ban integrity rules
  - HTTP integration tests for public catalog, private-room access denial, moderation actions, and room-delete behavior
- Manual
  - UI proof for public room discovery and full public-room detail loading
  - UI proof for private invite preview and invite-based join
  - moderation screenshots for admin grant or admin removal, ban-list inspection, and room delete

## Exit Criteria
- Room authorization boundaries match the contract and do not leak hidden room details.
- Moderation actions are auditable.
- Account deletion from Milestone 1 now triggers concrete rooms cleanup.
- The milestone ends with a green `./gradlew test` and a dated evidence note in `docs/evidence/`.
