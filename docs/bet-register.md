# Bet Register

## Active Bets

No active bets remain for the currently scoped MVP.

## Resolved Bets

| ID | Outcome | Resolution Signal | Status |
| --- | --- | --- | --- |
| B3 | The governed in-process XMPP path remains sufficient for the mandatory Jabber client, federation, admin-dashboard, and two-node load-validation scope. Milestone 8.2 connected 50 clients to node A and 50 clients to node B, exchanged 100 federated direct messages with zero transport failures, and kept the root single-node compose workflow unchanged. | `docs/evidence/2026-04-22-milestone-8-2-evidence.md`, `docs/evidence/2026-04-22-milestone-8-2-load-summary.json`, and `docs/evidence/2026-04-22-milestone-8-2-load-run.log` capture the clean two-node validation run and the saved dashboard screenshots. | Resolved |
| B2 | PostgreSQL plus in-process live state remains sufficient for unread fan-out and multi-tab presence in the MVP. Milestone 6.2 measured two-tab and two-browser presence propagation at 3 to 6 ms in the local compose topology, so Redis stays out of scope for now. | `docs/evidence/2026-04-22-milestone-6-2-evidence.md` and `docs/evidence/2026-04-22-milestone-6-2-live-metrics.json` capture the passing propagation timings and the close or AFK transition proofs. | Resolved |
| B1 | The governed implementation stack is Java 25, Gradle, Spring Boot, and PostgreSQL. | User architecture decision accepted in ADR 0001 and mirrored in the bootstrap. | Resolved |
| B5 | The MVP UI stays Spring Boot-served and authentication uses server-managed session cookies instead of JWT plus refresh tokens. | Accepted in ADR 0002. | Resolved |
| B6 | Rooms and direct dialogs share one logical chat contract, with HTTP for writes and WebSocket for server push. | Accepted in ADR 0003. | Resolved |
| B7 | When one user blocks another, any pending friendship request between the pair should be retired immediately instead of staying pending but unusable. | Milestone 3.2 now marks pending friendship requests between the blocked pair rejected during the same block transaction, which keeps the contacts page and later direct-dialog identity rules coherent. | Resolved |
| B4 | Account deletion outside rooms owned by the deleted user preserves historical messages through a tombstoned user identity rather than deleting prior authorship references. | Executable Milestone 1 implementation now revokes sessions, clears credentials, tombstones the user row, and exposes `AccountDeletionImpactPort` for later room and messaging cleanup. | Resolved |

## Open Questions
- Does the Milestone 4.1 history paging default of 50 messages with a max of 100 remain appropriate once the real timeline UI and larger message histories are exercised?
- Milestone 7 confirms that the current mirrored-local-username federation mapping is sufficient for the mandatory one-to-one direct-message scope because inbound federated sends can reuse the same direct-dialog eligibility checks as local HTTP and local XMPP. Revisit this only if a later scope adds non-mirrored remote identities or richer roster semantics.
- The load harness currently force-closes XMPP clients after the message phase so the 50-per-side validation run stays bounded. The resulting recent-session telemetry can show a mix of `DISCONNECTED` and `ERROR` for harness-owned sessions even when federation delivery and peer traffic counters are clean. Revisit this only if cleaner teardown telemetry becomes a release requirement.

## Update Rule
When a bet resolves, update this file and either:
- convert it into an ADR if the decision becomes architecture
- move the evidence into `docs/evidence/`
- replace it with a narrower follow-up bet
