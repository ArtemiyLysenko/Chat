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
- `2026-04-21-b3-xmpp-path-spike.md` narrows the mandatory XMPP implementation bet for Milestone 7 while keeping it aligned with ADR 0005.
