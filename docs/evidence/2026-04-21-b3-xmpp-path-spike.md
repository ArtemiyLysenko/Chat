# B3 XMPP Path Spike

Date: 2026-04-21
Milestone: 1 follow-up
Bet: B3

## Question
Which Java-compatible XMPP implementation path is the most pragmatic fit for Milestone 7 while preserving ADR 0005's accepted modular-monolith shape:
- one Spring Boot application per server node
- XMPP implemented as an adapter over shared application services
- root-local `docker compose` workflow
- governed PostgreSQL identity and browser session model
- required federation and later admin observability

## Research Path
- Primary research path:
  - Context7 for current Java XMPP library documentation.
  - Sonatype MCP for current artifact quality and version metadata.
- Context7 result:
  - Smack resolved successfully as `/igniterealtime/smack`.
- Sonatype result:
  - Sonatype MCP calls failed during this spike with an `Insufficient credits` response, so version and maturity confirmation fell back to official project documentation.
- Additional primary sources used after that fallback:
  - Smack project page: <https://www.igniterealtime.org/projects/smack/>
  - Apache Vysper documentation: <https://cwiki.apache.org/confluence/display/VYSPER/Documentation>
  - XMPP.org Apache Vysper software page: <https://xmpp.org/software/apache-vysper/>
  - Openfire custom authentication provider guide: <https://download.igniterealtime.org/openfire/docs/latest/documentation/implementing-authprovider-guide.html>
  - Openfire plugin developer guide: <https://download.igniterealtime.org/openfire/docs/latest/documentation/plugin-dev-guide.html>
  - Tigase server overview: <https://tigase.dev/tigase/_server/tigase-server>
  - Tigase product and operations overview: <https://tigase.net/xmpp-server/>

## Compared Paths

### Option A: focused in-process XMPP adapter inside the Spring Boot app
- Shape:
  - Keep one Spring Boot application per node.
  - Implement XMPP listener, stanza translation, and federation behavior in `modules/adapters/xmpp` and the existing feature modules.
  - Use Smack for interoperability tests, client harnesses, and any outbound connector code where it is helpful, but not as the server runtime.
- Evidence:
  - Context7 and the Smack project page both describe Smack as a Java XMPP client library that can be embedded in applications, which makes it a fit for test harnesses and connectors, not a drop-in embedded XMPP server.
- Fit with ADR 0005:
  - Full fit. This is the only path that preserves the accepted "one Spring Boot app per node" decision without reinterpretation.
- Fit with compose workflow:
  - Full fit. The two-node federation topology remains `app-a + db-a` and `app-b + db-b`.
- Governed auth fit:
  - Full fit. XMPP authentication and authorization can call the same identity, deletion, friendship, block, and messaging rules as the browser paths.
- Federation and observability fit:
  - Strong architectural fit because all required telemetry stays inside the governed application boundary and can be stored directly in PostgreSQL.
- Delivery risk:
  - Higher protocol implementation effort than using an off-the-shelf XMPP server, so the supported protocol surface must stay narrow in Milestone 7: authentication, basic presence, and one-to-one direct messages only.
- Result:
  - Recommended path under the currently accepted architecture.

### Option B: embedded Apache Vysper inside the Spring Boot JVM
- Shape:
  - Preserve one process by embedding Vysper into the application.
- Evidence:
  - The official Vysper documentation still says it can run stand-alone or embedded.
  - The official Apache documentation page shows it was last modified on May 31, 2011.
  - XMPP.org currently shows Apache Vysper with no claimed core or IM compliance.
- Fit with ADR 0005:
  - Structural fit, because it stays in-process.
- Practical risk:
  - Too high. The maintenance and compliance signals are materially weaker than the mandatory federation milestone requires.
- Result:
  - Rejected as the primary path.

### Option C: companion XMPP server such as Openfire or Tigase
- Shape:
  - Run a dedicated XMPP server beside `apps/api` and integrate via custom auth, plugins, REST, or a bridge.
- Evidence:
  - Openfire documents custom authentication providers and plugins, which confirms it is designed as a separate server product with extension points around it.
  - Tigase documents a standalone Java server with its own operational and admin model.
- Fit with ADR 0005:
  - Conflict. This changes the node shape from one Spring Boot application per server instance to a multi-process node with a separate primary XMPP runtime.
- Operational upside:
  - Mature federation, server administration, and monitoring features.
- Architectural cost:
  - It moves core protocol handling, some observability, and parts of the authentication surface outside the accepted modular-monolith boundary.
- Result:
  - Not recommended under the current ADR. If this path becomes necessary, it must be preceded by a new ADR that explicitly replaces or narrows ADR 0005.

## Recommendation
Keep ADR 0005 unchanged and plan Milestone 7 around **Option A: a focused in-process XMPP adapter inside the Spring Boot application**.

That means:
1. No companion Openfire or Tigase service in the normal Milestone 7 topology.
2. XMPP business behavior stays in the existing feature services and persistence model.
3. The XMPP adapter only implements the narrow protocol subset needed for Milestone 7:
   - governed authentication
   - basic presence
   - one-to-one direct messages
   - two-node federation for those direct messages
4. Smack is allowed as a supporting library for client interoperability tests, federation probes, and similar tooling, but not as a replacement for the server-side adapter.

## Milestone 7 Guardrails
- Do not broaden the protocol surface to rooms, MUC, or general-purpose XMPP server features in Milestone 7.
- Do not split a server node into `apps/api + external XMPP server` without a new ADR.
- Keep federation evidence on two identical Spring Boot nodes.
- Persist XMPP session and federation telemetry in the governed PostgreSQL schema so Milestone 8 dashboards query first-party data.

## Rejection Criteria
Open a new ADR before implementation continues if any of these prove true during the Milestone 7 startup spike:
- the in-process adapter cannot deliver client authentication and direct-message interoperability without duplicating identity rules
- two-node federation cannot be implemented reliably enough inside the Spring Boot adapter boundary
- admin observability requires a separate server product to be the primary telemetry owner

## Outcome
- B3 remains active, but it is now narrowed to an in-process XMPP adapter path that stays inside ADR 0005.
- Companion XMPP servers remain fallback options only if a later ADR explicitly changes the accepted architecture.
