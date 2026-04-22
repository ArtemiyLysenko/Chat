# Bet Register

## Active Bets

| ID | Bet | Why It Matters | Resolution Signal | Decision Deadline | Status |
| --- | --- | --- | --- | --- | --- |
| B3 | A Java-compatible XMPP implementation inside the governed Spring Boot application can deliver the mandatory Jabber client and federation scope without breaking the root compose workflow. | It determines the implementation shape for mandatory advanced scope and the feasibility of the two-server validation target. | One in-process implementation path supports Jabber clients, two-server federation, admin dashboards, and the required federation load evidence. | Before Jabber/federation milestone exit | Active |

## Resolved Bets

| ID | Outcome | Resolution Signal | Status |
| --- | --- | --- | --- |
| B2 | PostgreSQL plus in-process live state remains sufficient for unread fan-out and multi-tab presence in the MVP. Milestone 6.2 measured two-tab and two-browser presence propagation at 3 to 6 ms in the local compose topology, so Redis stays out of scope for now. | `docs/evidence/2026-04-22-milestone-6-2-evidence.md` and `docs/evidence/2026-04-22-milestone-6-2-live-metrics.json` capture the passing propagation timings and the close or AFK transition proofs. | Resolved |
| B1 | The governed implementation stack is Java 25, Gradle, Spring Boot, and PostgreSQL. | User architecture decision accepted in ADR 0001 and mirrored in the bootstrap. | Resolved |
| B5 | The MVP UI stays Spring Boot-served and authentication uses server-managed session cookies instead of JWT plus refresh tokens. | Accepted in ADR 0002. | Resolved |
| B6 | Rooms and direct dialogs share one logical chat contract, with HTTP for writes and WebSocket for server push. | Accepted in ADR 0003. | Resolved |
| B7 | When one user blocks another, any pending friendship request between the pair should be retired immediately instead of staying pending but unusable. | Milestone 3.2 now marks pending friendship requests between the blocked pair rejected during the same block transaction, which keeps the contacts page and later direct-dialog identity rules coherent. | Resolved |
| B4 | Account deletion outside rooms owned by the deleted user preserves historical messages through a tombstoned user identity rather than deleting prior authorship references. | Executable Milestone 1 implementation now revokes sessions, clears credentials, tombstones the user row, and exposes `AccountDeletionImpactPort` for later room and messaging cleanup. | Resolved |

## Open Questions
- Does the Milestone 4.1 history paging default of 50 messages with a max of 100 remain appropriate once the real timeline UI and larger message histories are exercised?
- Milestone 7.1 narrowed the local XMPP slice to TCP stream open, SASL `PLAIN`, bind, basic presence, and local one-to-one direct messages, with Smack `4.4.8` `smack-java8` plus `smack-tcp` plus `smack-im` used for interoperability tests only. The remaining B3 question is whether that same in-process shape stays pragmatic once two-node federation and traffic persistence are added.
- What two-server compose topology is the cleanest path to the required federation validation and 50-plus clients per side load test?

## Update Rule
When a bet resolves, update this file and either:
- convert it into an ADR if the decision becomes architecture
- move the evidence into `docs/evidence/`
- replace it with a narrower follow-up bet
