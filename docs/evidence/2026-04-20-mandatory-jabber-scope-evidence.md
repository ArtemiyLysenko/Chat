# Mandatory Jabber Scope Evidence

## Date
2026-04-20

## Scope
Documentation and governance update that makes the advanced Jabber/XMPP, federation, and administration requirements mandatory acceptance scope.

## Sources Reviewed
- `2026_04_18_AI_herders_jam_-_requirements_v3 1.docx`
- user-provided advanced requirements excerpt
- `docs/requirements-summary.md`
- `docs/architecture.md`
- `docs/mvp-delivery-plan.md`
- `docs/bet-register.md`

## Artifacts Updated
- `AGENTS.md`
- `README.md`
- `docs/requirements-summary.md`
- `docs/adlc-operating-model.md`
- `docs/adrs/0001-initial-stack-direction.md`
- `docs/adrs/0004-mandatory-xmpp-federation-scope.md`
- `docs/architecture.md`
- `docs/api-contracts.md`
- `docs/persistence-model.md`
- `docs/mvp-delivery-plan.md`
- `docs/bet-register.md`
- `docs/governance/checklist.md`
- `docs/evidence/README.md`

## Decisions Locked
- Jabber/XMPP client support is mandatory scope.
- Inter-server federation is mandatory scope.
- The web UI must include a Jabber connection dashboard and federation traffic statistics.
- Final acceptance must include two-server federation evidence with at least 50 connected clients per server and bidirectional messaging.

## Remaining Bets
- B2: validate the no-Redis path under the eventual two-server and multi-client workload.
- B3: choose the Java-compatible XMPP library and federation integration shape.
- B4: decide account deletion behavior for historical non-owned messages.

## Next Review Trigger
Revisit this scope decision when the Jabber/federation implementation milestone closes and load-test evidence is available.
