# Bet Register

## Active Bets

| ID | Bet | Why It Matters | Resolution Signal | Decision Deadline | Status |
| --- | --- | --- | --- | --- | --- |
| B2 | PostgreSQL plus in-process live state is sufficient for unread fan-out and multi-tab presence without adding Redis in the first iteration. | It keeps local setup simpler and preserves the single-node MVP shape, but presence and fan-out latency are the main scale risk. | Presence and message delivery targets remain stable in end-to-end and benchmark evidence for the MVP. | Before presence milestone exit | Active |
| B3 | A Java-compatible XMPP library integrated into the governed application stack can deliver the mandatory Jabber client and federation scope without breaking the root compose workflow. | It determines the implementation shape for mandatory advanced scope and the feasibility of the two-server validation target. | One implementation path supports Jabber clients, two-server federation, admin dashboards, and the required federation load evidence. | Before Jabber/federation milestone exit | Active |
| B4 | Account deletion outside rooms owned by the deleted user should preserve historical messages through a tombstone strategy rather than physically removing all prior authorship references. | The brief requires account removal and owned-room deletion, but it does not specify how existing history in other rooms or direct dialogs should behave. | Product governance picks a deletion rule and the identity milestone proves it with tests and evidence. | Before account deletion implementation closes | Active |

## Resolved Bets

| ID | Outcome | Resolution Signal | Status |
| --- | --- | --- | --- |
| B1 | The governed implementation stack is Java 25, Gradle, Spring Boot, and PostgreSQL. | User architecture decision accepted in ADR 0001 and mirrored in the bootstrap. | Resolved |
| B5 | The MVP UI stays Spring Boot-served and authentication uses server-managed session cookies instead of JWT plus refresh tokens. | Accepted in ADR 0002. | Resolved |
| B6 | Rooms and direct dialogs share one logical chat contract, with HTTP for writes and WebSocket for server push. | Accepted in ADR 0003. | Resolved |

## Open Questions
- Does B2 hold once presence and unread fan-out are exercised under realistic concurrent tab activity?
- Which Java-compatible XMPP library and protocol support level are the most pragmatic mandatory path for this repository?
- What two-server compose topology is the cleanest path to the required federation validation and 50-plus clients per side load test?
- Should account deletion preserve historical authorship as a tombstone, anonymize existing messages, or remove them from non-owned chats?

## Update Rule
When a bet resolves, update this file and either:
- convert it into an ADR if the decision becomes architecture
- move the evidence into `docs/evidence/`
- replace it with a narrower follow-up bet
