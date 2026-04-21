# Milestone 4.2 Evidence

- Date: 2026-04-21
- Slice: Milestone 4.2 reply semantics, edit or delete lifecycle, real room and direct-dialog timeline UI, composer UI, reply-target UI, tombstoned-author rendering, and HTTP-backed unread badge render or clear behavior

## What Changed
- Extended the shared messaging feature for reply-capable send, edit, delete, unread-count queries, and tombstoned-author-safe history rendering.
- Added JPA persistence for message reply linkage, edit timestamps, and logical delete timestamps.
- Replaced the placeholder room and direct-dialog panels with HTTP-driven timelines, composers, reply or edit banners, delete controls, and unread-badge refresh behavior.
- Updated the contacts and rooms HTTP surfaces so the browser can render unread badges from server-side unread markers.
- Updated the Milestone 4 docs to describe 4.2 precisely while keeping `/ws` and other live behavior deferred to 4.3.

## Automated Verification
- Command: `./gradlew :apps:api:test --tests 'edu.artemiy.chat.app.http.ApplicationHttpWiringTests.appliesRedirectRulesForProtectedAndAuthPages'`
  - Result: passed after updating the test to the real 4.2 direct-dialog workspace and the protected contacts route.
- Command: `./gradlew test`
  - Result: passed.

## Browser And Manual Proof
- Command: `docker compose up -d --build`
  - Result: app rebuilt and served successfully at `http://localhost:8080`.
- Supporting shell setup:
  - used cookie-backed `curl` sessions with CSRF headers to log in `scout213840` and `ghost213840`, join the proof room, send room and direct messages, and tombstone the ghost account
  - reseeded one fresh unread room message and one fresh unread direct message after the final UI rebuild so badge rendering and clear behavior could be rechecked cleanly
- Playwright proof:
  - opened `/app/contacts` and confirmed the direct unread badge rendered from HTTP data before opening the dialog
  - opened `/app/direct-dialogs/6a1d4de5-d460-49f5-95b5-c82db199c7a7`, confirmed the unread badge cleared, and sent `Captain direct response from the 4.2 browser proof.`
  - opened `/app` and confirmed the room unread badge rendered from HTTP data before opening the room
  - opened `/app?room=039011f5-f492-4ffc-87e5-6b6eeb471f09`, verified the unread badge cleared, and confirmed:
    - reply preview rendering
    - edited marker rendering on the captain reply
    - logical delete marker rendering
    - tombstoned-author rendering for the preserved deleted user row

## Saved Artifacts
- [2026-04-21-milestone-4-2-direct-unread-badge.png](2026-04-21-milestone-4-2-direct-unread-badge.png)
- [2026-04-21-milestone-4-2-direct-dialog-timeline.png](2026-04-21-milestone-4-2-direct-dialog-timeline.png)
- [2026-04-21-milestone-4-2-room-unread-badge.png](2026-04-21-milestone-4-2-room-unread-badge.png)
- [2026-04-21-milestone-4-2-room-timeline.png](2026-04-21-milestone-4-2-room-timeline.png)
- [2026-04-21-milestone-4-2-room-tombstone-delete.png](2026-04-21-milestone-4-2-room-tombstone-delete.png)

## Issues Found During Verification
- Browser proof exposed a real CSS regression: `.chat-target-banner` overrode the HTML `hidden` attribute, leaving empty reply or edit panels visible.
  - Fix: added a global `[hidden] { display: none !important; }` rule.
- Fresh full-suite verification exposed an outdated wiring assertion that still expected the old placeholder direct-dialog page.
  - Fix: updated the routing test to the real 4.2 direct-dialog workspace and added coverage for `/app/contacts`.

## Result Summary
- Milestone 4.2 is implemented and verified.
- The room and direct-dialog surfaces now behave like real HTTP-backed chat screens for send, reply, edit, delete, history load, and unread clearing.
- Milestone 4.3 realtime `/ws`, live unread updates, and live session-revocation handling remain unimplemented by design.

## Remaining Risk
- The UI remains intentionally poll or refresh driven. Because 4.3 is still deferred, users do not receive live push for message or unread changes until they reopen or refresh the relevant surface.
