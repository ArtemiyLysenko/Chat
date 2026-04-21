# Milestone 6.1 Evidence

- Date: 2026-04-22
- Slice: Milestone 6.1 backend foundation for per-tab presence persistence and derived `ONLINE` or `AFK` or `OFFLINE` state

## Scope Validated

- Forward-only `session_tabs` persistence for authenticated browser-tab state
- Presence feature module with register, activity, close, and derive operations
- Presence derivation rules locked to the milestone time windows:
  - connected while `last_ping_at` is within 45 seconds
  - `ONLINE` while any connected tab has activity within 60 seconds
  - `AFK` when connected tabs remain but no active tab is within 60 seconds
  - `OFFLINE` when no connected tabs remain
- Persistence filtering that excludes revoked or expired sessions from derived presence

## Code And Doc Surface Checked

- Added `V0019__session_tabs.sql` with the required unique `(session_id, tab_key)` constraint and presence lookup indexes
- Added `modules/features/presence` service and SPI surface for tab registration, activity updates, close handling, and multi-user derivation
- Added JPA persistence support under `modules/adapters/persistence-jpa/presence`
- Added service and persistence tests for timeout windows, multi-tab behavior, session filtering, and tab-key uniqueness

## Automated Verification

- Targeted presence verification after the review-driven test additions:

```bash
./gradlew :modules:features:presence:test --tests '*DefaultPresenceServiceTests' :modules:adapters:persistence-jpa:test --tests '*PresenceRepositoryIntegrationTests' --no-daemon
```

- Outcome: `BUILD SUCCESSFUL`

- Full project verification after the final 6.1 edits:

```bash
./gradlew test --no-daemon
```

- Outcome: `BUILD SUCCESSFUL`

## Reviewer Pass Notes

- Reviewed the slice for timeout-boundary drift, future client timestamp skew, and stale-session leakage.
- Added explicit service coverage for:
  - the inclusive 45-second and 60-second timeout boundaries
  - clamping future activity timestamps to server time before persistence

## Result

- Milestone 6.1 is complete and verified as a backend-only slice.
- Milestone 6 remains open. WebSocket `tab.activity` or `tab.closed`, `presence.updated` fan-out, B2 resolution evidence, and presence badges remain for Milestone 6.2.
