# B3 XMPP Path Spike

Date: 2026-04-20
Milestone: 1
Bet: B3

## Question
Which Java-compatible XMPP implementation path is the most pragmatic fit for Milestone 7, given:
- one governed Spring Boot app in `apps/api`
- root-local `docker compose` workflow
- PostgreSQL-backed identity and cookie sessions already implemented in Milestone 1
- mandatory federation
- mandatory admin observability for XMPP connections and federation traffic

## Compared Paths

### Option A: same-process embedded XMPP server with Apache Vysper
- Source: [Apache Vysper documentation](https://cwiki.apache.org/confluence/display/VYSPER/Documentation)
- Source: [XMPP.org software page for Apache Vysper](https://xmpp.org/software/apache-vysper/)
- Fit:
  - Best theoretical fit for “one process” because it can run embedded in the Spring Boot JVM.
  - Weak practical fit for the hackathon target because the official Apache documentation page was last modified on May 31, 2011 and the XMPP.org software page shows no claimed core compliance.
- Observability:
  - Would require us to build connection, federation, and traffic instrumentation mostly from scratch.
- Federation:
  - Server-to-server support exists conceptually, but the visible compliance and project recency signals are weak for a milestone that must prove two-server federation under load.
- Result:
  - Rejected as the primary path. The compliance and maintenance risk are too high for the mandatory federation milestone.

### Option B: companion Openfire server, integrated with the Spring Boot app
- Source: [Openfire overview](https://download.igniterealtime.org/openfire/docs/latest/documentation/)
- Source: [Openfire 5.0.4 release page](https://www.igniterealtime.org/projects/openfire/index.jsp)
- Source: [Openfire custom AuthProvider guide](https://download.igniterealtime.org/openfire/docs/latest/documentation/implementing-authprovider-guide.html)
- Source: [Openfire plugin developer guide](https://download.igniterealtime.org/openfire/docs/latest/documentation/plugin-dev-guide.html)
- Source: [Openfire monitoring plugin readme](https://www.igniterealtime.org/projects/openfire/plugins/monitoring/readme.html)
- Fit:
  - Strong compose fit: it is a standalone Java server that can run as a peer service beside `apps/api`.
  - Strong Spring Boot fit: the chat app can stay the governed HTTP and cookie-auth surface, while Openfire handles XMPP client protocol and server-to-server federation.
  - Strong governed-auth fit: the official `AuthProvider` extension point gives a supported path to authenticate against our governed identity store or a thin auth bridge without moving browser auth away from `CHAT_SESSION`.
- Observability:
  - Strongest option of the three for the hackathon. Openfire already has an admin console, plugin model, and a monitoring plugin that exposes statistics and archived chat telemetry.
  - The app can ingest or proxy the relevant connection and traffic data into the governed admin screens in Milestone 8.
- Federation:
  - Best practical fit. Openfire is a real XMPP server with mature server-to-server capabilities and an operations model that naturally maps to a two-node compose validation.
- Result:
  - Recommended path.

### Option C: companion Tigase XMPP Server
- Source: [Tigase server overview](https://docs.tigase.net/en/master/Tigase_Administration/XMPP_Server/About_Tigase_XMPP_Server.html)
- Source: [Tigase feature page](https://tigase.net/xmpp-server/)
- Source: [Tigase server feature summary](https://tigase.dev/tigase/_server/tigase-server)
- Fit:
  - Technically capable and Java-based.
  - Operationally heavier than the repo currently needs. The documentation emphasizes clustering, broad monitoring, and large-scale deployment rather than a compact hackathon compose path.
- Observability:
  - Good monitoring story, but it is centered on Tigase’s own admin and metrics ecosystem rather than a minimal bridge into our governed app.
- Federation:
  - Strong on paper.
- Result:
  - Viable fallback, but not the first recommendation because the integration and operations surface appears broader than necessary for this repository.

## Recommendation
Recommend **Option B: Openfire as a companion XMPP service in the compose stack**.

Reasoning:
- It keeps the governed application stack intact. Spring Boot remains the browser and REST application, while Openfire handles XMPP protocol duties.
- It respects the Milestone 1 auth model. Browser auth stays same-origin cookie-based, while XMPP clients authenticate through a supported Openfire extension that reads the governed identity source.
- It is the cleanest path to federation evidence. Two Openfire nodes in compose are more realistic than trying to harden an embedded library into a federating server within the remaining milestone budget.
- It gives the best observability runway. Connection state, statistics, and plugin hooks exist today instead of needing a bespoke metrics plane.

## Recommended Milestone 7 Shape
1. Run one Openfire container per server node beside one `apps/api` container.
2. Implement an Openfire plugin or auth provider that validates credentials against the governed chat identity store and denies tombstoned users.
3. Keep Spring Boot as the source of truth for browser sessions, account lifecycle, room rules, and later messaging authorization.
4. Bridge required XMPP connection and traffic telemetry into PostgreSQL tables owned by the Spring Boot app so Milestone 8 dashboards query governed data.

## Rejection Criteria

Reject the Openfire path if any of these prove true during Milestone 7 startup spikes:
- We cannot implement governed authentication without duplicating passwords or bypassing the tombstoned-user rules.
- Openfire connection and federation telemetry cannot be extracted cleanly enough to power the required admin dashboards.
- Two-node compose federation proves materially unstable for the required validation path.

If Openfire is rejected, the next candidate should be Tigase, not Vysper. Vysper should stay rejected unless new evidence shows current compliance and federation support far beyond the public signals available today.

## Outcome
- B3 remains active, but narrowed.
- Milestone 7 should plan around an **Openfire companion service** unless early integration work trips the rejection criteria above.
