# Milestone 4.4 Evidence

- Date: 2026-04-21
- Slice: Milestone 4.4 browser live-update closeout, reconnect behavior, live session-revocation handling, and Milestone 4 completion

## What Changed
- Added a reusable browser `/ws` live-updates client with reconnect backoff, `subscription.resume`, post-reconnect HTTP refresh, and session-revocation redirect handling.
- Wired the room shell to live `message.*` and `unread.updated` events so joined-room badges and open room timelines refresh without manual reload.
- Wired the contacts workspace and direct-dialog page to live direct-message unread and timeline updates.
- Added login flash messaging so live session revocation redirects land with a visible sign-in explanation.
- Updated the Milestone 4 status docs and shipped browser notes to reflect that browser live wiring is complete and Milestone 4 is now closed.

## Automated Verification
- Playwright MCP smoke:
  - loaded `http://localhost:8080/login`
  - confirmed the rendered sign-in page after the 4.4 browser changes
- Browser proof:
  - executed a temporary local Playwright proof script from the repository workspace
  - used the local Playwright runtime installed under `/tmp/chat-playwright-proof`
  - result: passed
- Full regression suite:
  - `./gradlew test --no-daemon`
  - result: passed

## Browser And Timing Proof
- Live room unread badge:
  - Browser user `m44b1776802252323` stayed on `/app` with the joined-room list visible.
  - A second authenticated user sent a room message over HTTP.
  - The joined-room unread badge appeared live in `56 ms`.
- Live room timeline:
  - The same browser opened the room timeline.
  - A second room message appeared live in `38 ms` without pressing Refresh.
- Live direct unread badge:
  - The browser opened `/app/contacts`.
  - A second authenticated user sent a direct message over HTTP.
  - The direct unread badge appeared live in `40 ms`.
- Live direct timeline:
  - The browser opened `/app/direct-dialogs/04e3ac69-61dc-4469-a1ff-0f946514b1f9`.
  - A second direct message appeared live in `35 ms` without pressing Refresh.
- Reconnect proof:
  - The proof restarted the app container with `docker compose restart app`.
  - The app recovered health in `3873 ms`.
  - After reconnect, the next live direct message appeared in `81 ms` without reopening the page.
- Live session revocation:
  - Revoking the browser’s current session redirected the page to `/login` in `38 ms`.
  - The redirected login page rendered the flash message `This session was revoked. Sign in again.`

## Saved Artifacts
- [2026-04-21-milestone-4-4-room-unread-live.png](2026-04-21-milestone-4-4-room-unread-live.png)
- [2026-04-21-milestone-4-4-room-live-timeline.png](2026-04-21-milestone-4-4-room-live-timeline.png)
- [2026-04-21-milestone-4-4-direct-unread-live.png](2026-04-21-milestone-4-4-direct-unread-live.png)
- [2026-04-21-milestone-4-4-direct-live-timeline.png](2026-04-21-milestone-4-4-direct-live-timeline.png)
- [2026-04-21-milestone-4-4-direct-live-after-reconnect.png](2026-04-21-milestone-4-4-direct-live-after-reconnect.png)
- [2026-04-21-milestone-4-4-session-revoked.png](2026-04-21-milestone-4-4-session-revoked.png)
- [2026-04-21-milestone-4-4-live-metrics.json](2026-04-21-milestone-4-4-live-metrics.json)

## Issues Found During Verification
- The first direct-dialog proof run exposed a real race: the page started its live socket only after the initial HTTP load finished, which left a narrow gap where a newly opened dialog could miss the next live message.
  - Fix: start the live client before the initial page bootstrap on the room, contacts, and direct-dialog browser surfaces.

## Result Summary
- Milestone 4 is implemented and verified.
- Room and direct-dialog browser surfaces now receive live message and unread updates over `/ws` while HTTP remains the write path.
- Browser reconnect now resumes the socket and refreshes HTTP-backed chat state after recovery.
- Live session revocation now removes the active socket and redirects the affected browser tab to sign in.
