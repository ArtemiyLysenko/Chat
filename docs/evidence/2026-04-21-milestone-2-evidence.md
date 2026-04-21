# Milestone 2 Evidence

## Date
2026-04-21

## Scope
Milestone 2 room catalog, membership, moderation, and rooms-side account deletion cleanup:
- public room discovery and public-room detail loading
- private-room invite preview
- invite-based private-room join through the shared join endpoint
- admin grant and admin-removal moderation flows
- room ban inspection and unban behavior
- room deletion
- account deletion cleanup for owned rooms, memberships, invites, and target-side bans

## Sources Reviewed
- `docs/milestones/02-rooms-moderation.md`
- `docs/mvp-delivery-plan.md`
- `docs/api-contracts.md`
- `docs/persistence-model.md`
- `docs/architecture.md`
- `docs/bet-register.md`
- `docs/governance/checklist.md`
- `docs/evidence/README.md`
- `README.md`

## Code And Docs Validated
- `modules/features/rooms`
- `modules/adapters/persistence-jpa`
- `apps/api` room HTTP and static UI wiring
- `docs/api-contracts.md`
- `docs/milestones/02-rooms-moderation.md`
- `docs/architecture.md`
- `docs/persistence-model.md`
- `README.md`

## Verification Commands

### Automated verification
- `./gradlew :modules:features:rooms:test --no-daemon`
  Result: passed
- `./gradlew :modules:adapters:persistence-jpa:test --tests '*RoomsRepositoryIntegrationTests' --no-daemon`
  Result: passed
- `./gradlew :apps:api:test --tests '*RoomsHttpIntegrationTests' --no-daemon`
  Result: passed
- `./gradlew test --no-daemon`
  Result: passed

### Browser-proof runtime commands
- `POSTGRES_PORT=15432 docker compose -f /Users/artemiy/Projects/Chat/compose.yaml up -d db`
  Result: passed, created `chat_default`, created `chat_postgres-data`, and started `chat-db-1`
- `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:15432/chat SPRING_DATASOURCE_USERNAME=chat SPRING_DATASOURCE_PASSWORD=chat SERVER_PORT=18180 ./gradlew :apps:api:bootRun --no-daemon`
  Result: passed, app started on `http://127.0.0.1:18180/` after applying migrations through `V0009`
- `npx -y playwright@1.55.0 install chromium`
  Result: passed, downloaded Chromium, FFMPEG, and Chromium Headless Shell into the local Playwright cache
- `mkdir -p /tmp/chat-playwright-proof && cd /tmp/chat-playwright-proof && npm init -y && npm install playwright@1.55.0`
  Result: passed, installed a local Playwright runtime used for browser proof after the MCP-backed Playwright tools failed to initialize
- `node /tmp/chat-playwright-proof/milestone2-proof.js`
  Result: passed, created clean Milestone 2 browser state and wrote the six screenshots listed below
- `POSTGRES_PORT=15432 docker compose -f /Users/artemiy/Projects/Chat/compose.yaml down -v`
  Result: passed, removed the local PostgreSQL container, network, and temporary volume

## Browser And Manual Flow Evidence
- `docs/evidence/2026-04-21-milestone-2-public-room-discovery.png`
  Authenticated viewer `viewer382458` sees public room `Town Square 382458` in the catalog and can open full public-room details before joining.
- `docs/evidence/2026-04-21-milestone-2-private-invite-preview.png`
  Invited user `invitee382458` sees only the room name and owner for `Backstage 382458` before joining.
- `docs/evidence/2026-04-21-milestone-2-invite-join.png`
  The same invited user joins through `POST /api/rooms/{roomId}/join` and then sees full room details, membership, and the leave action.
- `docs/evidence/2026-04-21-milestone-2-admin-removal.png`
  Owner `owner382458` sees `moderator382458` as `ADMIN` in `Town Square 382458`, with the explicit `Remove + ban` control shown for admin removal.
- `docs/evidence/2026-04-21-milestone-2-ban-list.png`
  After admin removal, the current ban list shows the banned user, the banning actor, and the recorded timestamp.
- `docs/evidence/2026-04-21-milestone-2-room-delete.png`
  After deleting `Disposable 382458`, the room is absent from the owner shell and no room detail remains selected.

## Current Findings
- The implemented room service, repository adapter, HTTP layer, UI, and tests already satisfy the locked Milestone 2 decisions.
- Private-room invitees receive only an `INVITED_PREVIEW` containing the room name and owner.
- `POST /api/rooms/{roomId}/join` is the single membership-entry path for public rooms and invited private rooms.
- Removing a regular member does not create a ban.
- Removing an admin through member removal removes the membership, creates a ban, and records moderation.
- Account deletion now performs rooms cleanup through `AccountDeletionImpactPort`.

## Remaining Caveat
- Room deletion is a hard delete of room-owned state. The implementation records `ROOM_DELETED` in the transaction, but moderation records remain room-owned and are removed by the room delete cascade.
- The Playwright MCP browser tools could not be used directly because they tried to create `/.playwright-mcp` on a read-only filesystem. Browser proof still used Playwright, but through a local runtime installed under `/tmp/chat-playwright-proof`.
