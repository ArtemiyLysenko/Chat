# Milestone 6.2 Evidence

- Date: 2026-04-22
- Slice: Milestone 6 closeout for `/ws` tab activity, presence fan-out, browser badges, and B2 resolution

## Scope Validated

- `tab.activity` and `tab.closed` control handling on the authenticated `/ws` channel
- `presence.updated` fan-out to friends and users sharing rooms
- Derived presence returned by `/api/contacts` and `/api/rooms/{roomId}`
- Live presence badges in the contacts view, direct-dialog contacts sidebar, and shared-room member view
- Multi-tab and multi-browser transitions across `ONLINE`, `AFK`, and `OFFLINE`
- B2 resolution in favor of PostgreSQL plus in-process live state without Redis

## Code And Doc Surface Checked

- Added `ChatWebSocketPresenceCoordinator` to bind stable tab keys to sockets, translate `tab.activity` and `tab.closed` into presence-service calls, and trigger timeout-driven presence refreshes
- Added `PresenceAudienceQuery` plus JPA persistence support so presence fan-out can target friends and shared-room viewers
- Extended `/api/contacts` friend rows and `/api/rooms/{roomId}` member rows with derived `presence`
- Wired the browser live-update client to send stable per-tab heartbeats and close signals, then refreshed presence-sensitive UI on `presence.updated`
- Updated README, architecture notes, the API contract, the bet register, and the evidence index to reflect Milestone 6 completion and B2 resolution

## Automated Verification

- Targeted verification after the review fix:

```bash
./gradlew :apps:api:test --tests '*ApplicationArchitectureTests' --tests '*ChatWebSocketIntegrationTests' --tests '*ContactsHttpIntegrationTests' --tests '*RoomsHttpIntegrationTests' --no-daemon
```

- Outcome: `BUILD SUCCESSFUL`

- Full project verification after the final 6.2 edits:

```bash
./gradlew test --no-daemon
```

- Outcome: `BUILD SUCCESSFUL`

## Browser And Manual Verification

- Live app boot:

```bash
APP_PORT=18080 docker compose up -d --build app
```

- Browser proof used a real compose-backed app plus Playwright-driven tabs and windows.
- Saved artifacts:
  - contacts friend badge online proof: [2026-04-22-milestone-6-2-contacts-online.png](2026-04-22-milestone-6-2-contacts-online.png)
  - shared-room member online proof: [2026-04-22-milestone-6-2-room-online.png](2026-04-22-milestone-6-2-room-online.png)
  - shared-room member offline proof: [2026-04-22-milestone-6-2-room-offline.png](2026-04-22-milestone-6-2-room-offline.png)
  - latency measurements and transition notes: [2026-04-22-milestone-6-2-live-metrics.json](2026-04-22-milestone-6-2-live-metrics.json)

## Timing And B2 Result

- The saved metrics file records these observed end-to-end propagation times in the local compose topology:
  - two tabs: online in 6 ms and offline in 4 ms
  - two browser windows: online in 5 ms, AFK in 5 ms, back online in 3 ms, and offline in 3 ms
- All observed transitions stayed well under the milestone’s two-second benchmark target.
- Result: B2 resolves in favor of PostgreSQL plus in-process live state for the MVP. Redis is not justified by the current evidence.

## Result

- Milestone 6.2 exit criteria are met.
- Milestone 6 is now complete with green targeted verification, browser-backed presence evidence, and an evidence-backed B2 decision.
