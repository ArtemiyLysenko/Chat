# Architecture Baseline Evidence

## Date
2026-04-20

## Scope
Initial solution-architecture baseline for the chat MVP before feature implementation.

## Sources Reviewed
- `2026_04_18_AI_herders_jam_-_requirements_v3 1.docx`
- `docs/requirements-summary.md`
- `docs/adlc-operating-model.md`
- `docs/adrs/0001-initial-stack-direction.md`

## Artifacts Produced
- Expanded `docs/architecture.md`
- New `docs/api-contracts.md`
- New `docs/persistence-model.md`
- New `docs/mvp-delivery-plan.md`
- ADR 0002 and ADR 0003
- Updated `docs/bet-register.md`
- Updated `docs/governance/checklist.md`
- Updated repository documentation links in `README.md`

## Decisions Locked
- MVP UI remains Spring Boot-served.
- Authentication uses server-managed session cookies backed by PostgreSQL.
- Rooms and direct dialogs share one logical chat contract.
- HTTP is the mutation path and WebSocket is the realtime fan-out path.
- Cursor-based history and request-time attachment authorization are mandatory from the start.
- This baseline is superseded on advanced scope by `2026-04-20-mandatory-jabber-scope-evidence.md`.

## Remaining Bets
- B2: validate the no-Redis MVP path with presence and fan-out evidence.
- B3: choose the Java-compatible XMPP and federation integration path for the now-mandatory advanced scope.
- B4: decide how account deletion should preserve or transform historical non-owned messages.

## Next Review Trigger
Revisit this baseline at the end of Milestone 1 after identity, session, and account deletion behavior have executable evidence.
