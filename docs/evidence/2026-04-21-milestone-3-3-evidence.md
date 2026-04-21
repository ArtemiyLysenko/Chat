# Milestone 3.3 Evidence

- Date: 2026-04-21
- Milestone: Milestone 3.3, which closes full Milestone 3
- Requirement slice: direct-dialog ensure or fetch, stable direct-dialog identity, dedicated direct-dialog placeholder view, contacts-side account deletion cleanup, and Milestone 3 closeout docs

## Code And Doc Scope Validated
- `POST /api/direct-dialogs/{userId}` now ensures or reuses a stable direct dialog for an eligible friend pair.
- `direct_dialogs` persistence now enforces ordered-pair uniqueness and preserves dialog rows across later friendship removal, blocks, and account deletion.
- Account deletion now removes friend requests, friendships, and user blocks involving the deleted user while preserving `direct_dialogs`.
- The contacts page now links to a dedicated direct-dialog placeholder page that shows the participant, stable dialog id, and explicit Milestone 4 defer message.
- Milestone 3 docs now describe the completed slice and the Milestone 4 boundary accurately.

## Automated Verification
- Command: `./gradlew test`
- Result: passed
- Notes:
  - service coverage now includes eligible direct-dialog ensure, stable reuse, ineligible denial, preservation after friendship removal, preservation after block, and account deletion cleanup while preserving the dialog row
  - persistence coverage now includes `direct_dialogs` ordered-pair uniqueness, stable lookup reuse, and preserved rows after relationship cleanup
  - HTTP integration coverage now includes create or fetch for `POST /api/direct-dialogs/{userId}`, ineligible denial, and account deletion preserving `direct_dialogs`

## Browser Proof
- Stack command: `docker compose up -d --build`
- Database reset command:
  `docker compose exec -T db psql -U chat -d chat -c "TRUNCATE TABLE direct_dialogs, user_blocks, friendships, friendship_requests, moderation_audit_events, room_bans, room_invites, room_memberships, rooms, user_sessions, password_reset_tokens, users CASCADE;"`
- Result: passed
- Manual sequence:
  - registered `captain` and `scout`
  - created a friend request from `captain` to `scout`
  - accepted the request as `scout`
  - opened the direct-dialog placeholder as `captain`
  - reopened the same direct-dialog placeholder as `scout`
  - blocked `captain` as `scout` after dialog creation to confirm the existing Milestone 3 contacts flow still behaved correctly
- Observed dialog id reuse:
  - captain view created dialog id `1c430e77-0652-4093-962f-0077ee113724`
  - scout view reused the same dialog id `1c430e77-0652-4093-962f-0077ee113724`
- Saved screenshots:
  - `docs/evidence/2026-04-21-milestone-3-contacts-accepted.png`
  - `docs/evidence/2026-04-21-milestone-3-direct-dialog-captain.png`
  - `docs/evidence/2026-04-21-milestone-3-direct-dialog-scout.png`
  - `docs/evidence/2026-04-21-milestone-3-blocked-users.png`

## Result Summary
- Full Milestone 3 is complete and verified.
- Stable direct-dialog identity now exists before Milestone 4 messaging work begins.
- The dedicated placeholder page stays messaging-free as intended.
- Contacts-side account deletion cleanup is now real and preserves future direct-dialog history linkage.

## Bets And Follow-Up
- No new architecture bet was opened by this slice.
- Next milestone boundary remains Milestone 4: actual message send, direct or room history, unread markers, and realtime message updates.
