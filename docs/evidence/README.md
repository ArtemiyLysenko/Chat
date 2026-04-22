# Evidence Guide

Store milestone evidence in this directory as dated Markdown notes, screenshots, logs, or benchmark outputs.

## Minimum Contents Per Milestone
- milestone name and date
- brief requirement slice covered
- code or doc changes being validated
- commands or manual steps executed
- result summary, including failures or known gaps
- links to screenshots, logs, or benchmark artifacts when relevant
- bets resolved, narrowed, or opened by the evidence
- for Jabber or federation milestones, explicit server-topology notes and client-count evidence

## Naming Convention
- Markdown note: `YYYY-MM-DD-<milestone>-evidence.md`
- Screenshot: `YYYY-MM-DD-<milestone>-<screen>.png`
- Log or benchmark artifact: `YYYY-MM-DD-<milestone>-<artifact>.log`

## Current Baseline
- `2026-04-20-architecture-baseline-evidence.md` captures the initial solution-architecture package landed before feature implementation.
- `2026-04-20-mandatory-jabber-scope-evidence.md` captures the later scope decision that made the advanced Jabber and federation requirements mandatory.
- `2026-04-20-milestone-1-evidence.md` captures the executable Milestone 1 identity, session, security, and compose verification results.
- `2026-04-21-milestone-2-evidence.md` captures the verified Milestone 2 rooms, moderation, account-deletion cleanup, and browser-proof package, including the saved room-flow screenshots.
- `2026-04-21-milestone-3-1-evidence.md` captures the verified Milestone 3.1 contacts slice, including friend-request lifecycle coverage, contacts API checks, and saved browser screenshots for the dedicated contacts page.
- `2026-04-21-milestone-3-2-evidence.md` captures the verified Milestone 3.2 contacts slice, including remove-friend, block or unblock, blocked-request denial, and blocked-users subsection browser proof.
- `2026-04-21-milestone-3-3-evidence.md` captures the verified Milestone 3.3 closeout, including direct-dialog ensure or fetch, direct-dialog placeholder screenshots, same-pair id reuse, and contacts-side account-deletion cleanup evidence.
- `2026-04-21-milestone-4-1-evidence.md` captures the verified Milestone 4.1 backend slice, including messaging persistence, HTTP room and direct-dialog send or history checks, `before` cursor proof, and forward-only read-marker evidence without claiming later Milestone 4 UI or WebSocket work.
- `2026-04-21-milestone-4-2-evidence.md` captures the verified Milestone 4.2 slice, including reply or edit or delete HTTP flows, real room and direct-dialog timelines, unread badge rendering and clearing, and the saved browser screenshots for those flows without claiming Milestone 4.3 realtime work.
- `2026-04-21-milestone-4-3-evidence.md` captures the verified Milestone 4.3 backend slice, including authenticated `/ws` handshake checks, realtime message or unread fan-out, and live session-revocation enforcement without claiming the later Milestone 4.4 browser wiring work.
- `2026-04-21-milestone-4-4-evidence.md` captures the verified Milestone 4 closeout, including live room or direct-dialog browser updates, reconnect after app restart, live session-revocation redirect, and the linked screenshots plus timing metrics.
- `2026-04-21-milestone-5-1-evidence.md` captures the verified Milestone 5.1 backend slice, including attachment migrations, filesystem blob storage, upload or metadata or download authorization, room-removal access-loss proof, and the contract note that `messageId` stays reserved in this slice.
- `2026-04-21-milestone-5-2-evidence.md` captures the verified Milestone 5 closeout, including room and direct-dialog attachment UI, upload-button and paste-image browser proof, removed-member access-loss verification, room-delete blob cleanup, and the linked screenshots.
- `2026-04-22-milestone-6-1-evidence.md` captures the verified Milestone 6.1 backend foundation, including `session_tabs`, presence derivation rules, session filtering, timeout-boundary coverage, and the green targeted plus full test runs without claiming the later Milestone 6.2 WebSocket or UI work.
- `2026-04-22-milestone-6-2-evidence.md` captures the verified Milestone 6 closeout, including `/ws` tab activity and close handling, `presence.updated` fan-out, contacts and room-member presence badges, the saved browser screenshots, the measured two-tab and two-browser propagation timings, and the evidence-backed B2 resolution.
- `2026-04-22-milestone-7-1-evidence.md` captures the verified Milestone 7.1 XMPP foundation, including the in-process adapter shape, governed Jabber login, tombstoned-user denial, basic presence between connected friends, local one-to-one direct-message interoperability, the Smack test-client dependency decision, and the fresh targeted plus full test runs.
- `2026-04-22-milestone-7-2-evidence.md` captures the verified Milestone 7.2 federation slice, including the persisted peer or traffic schema, two-node A-to-B and B-to-A direct-message federation proof, the governed compose topology under `infra/docker/compose.federation.yaml`, the gateway root-cause fix, and the narrowed mirrored-user assumption for the current v1 mapping.
- `2026-04-21-b3-xmpp-path-spike.md` narrows the mandatory XMPP implementation bet for Milestone 7 while keeping it aligned with ADR 0005.
