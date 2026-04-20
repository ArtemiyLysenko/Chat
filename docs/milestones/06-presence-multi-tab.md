# Milestone 6: Presence And Multi-Tab Behavior

## Summary
This milestone adds accurate `ONLINE`, `AFK`, and `OFFLINE` presence derived from authenticated browser tabs and WebSocket activity.
It also resolves the Redis-related B2 bet with measurement rather than assumption.

## Deliverables
- Presence state derivation for multi-tab browser sessions.
- Presence indicators in contacts and shared-room member views.
- WebSocket client control messages for tab activity and tab close.
- Evidence that PostgreSQL plus in-process live state is or is not sufficient for the MVP.

## Out Of Scope
- Native mobile push presence.
- Redis or separate presence infrastructure unless B2 fails.
- XMPP-side presence beyond what Milestone 7 adds for basic interoperability.

## Implementation Plan
1. Database and persistence adapter
   - Add Flyway migration for `session_tabs`.
   - Implement persistence support in `modules/adapters/persistence-jpa/presence`.
2. Presence feature module
   - Implement `api`, `application`, and `spi` packages in `modules/features/presence`.
   - Keep `PresenceState` as the only public presence type.
   - Add services for:
     - register tab connection
     - update tab activity
     - close tab
     - derive user presence
     - fan out updates to relevant watchers
3. Tab identity and client behavior
   - Use `sessionStorage` tab keys so each tab has a stable identity across reloads but a distinct identity from other tabs.
   - Send a heartbeat every 15 seconds while the tab is open.
   - Update activity on user input and when the document becomes visible again.
   - Send `tab.closed` as a best-effort signal on unload.
4. Derivation rules
   - A tab is considered connected while `last_ping_at` is within the last 45 seconds.
   - A user is `ONLINE` if at least one connected tab has user activity within the last 60 seconds.
   - A user is `AFK` if at least one connected tab exists and none have activity within the last 60 seconds.
   - A user is `OFFLINE` if no connected tabs remain.
5. WebSocket integration
   - Add `tab.activity` and `tab.closed` client control messages to the existing `/ws` channel.
   - Fan out `presence.updated` events to:
     - contacts who can see the user
     - users sharing rooms with the user
   - Do not add new REST write endpoints for presence.
6. UI integration
   - Add presence badges to contacts and shared-room member lists.
   - Keep presence rendering lightweight and client-side.
7. B2 validation
   - Measure presence propagation and message delivery after adding this feature.
   - Only introduce Redis if the benchmark evidence shows the current shape cannot meet the target.

## Contract And Behavior Locks
- Use the WebSocket control messages and event types already defined in `docs/api-contracts.md`.
- Presence is derived state, not a user-editable status.
- Browser tabs are the presence source of truth for the web UI.

## Tests And Evidence
- Automated
  - service tests for derivation from multiple tab records and timeout windows
  - persistence tests for unique tab keys per session
  - WebSocket integration tests for `tab.activity`, `tab.closed`, and `presence.updated`
- Manual
  - two-tab and two-browser probes for `ONLINE`, `AFK`, and `OFFLINE`
  - benchmark note showing presence propagation under two seconds
  - B2 evidence note deciding whether PostgreSQL plus in-process live state remains sufficient

## Exit Criteria
- B2 is resolved with evidence.
- Presence stays accurate through reconnects, inactive tabs, and closed tabs.
- The milestone ends with a green `./gradlew test` and a dated evidence note in `docs/evidence/`.
