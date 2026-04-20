# ADLC Operating Model

Source: [adlc.io](https://www.adlc.io/)

## What ADLC Means For This Repo
ADLC treats software delivery as a loop where agents execute and humans govern. The relevant operating implications for this project are:

- `Intent`: define bets, not fake certainty.
- `Generate`: generate code, tests, docs, and infra together.
- `Validate`: validate continuously while generating.
- `Govern`: keep human review focused on alignment and risk decisions.
- `Deploy`: keep release paths simple and recoverable.
- `Observe`: collect signals that feed the next bet.

## Five Principles Applied Here
- Concurrency over sequencing: work on implementation, tests, docs, and runbooks together.
- Governance over execution: use human time for product and risk decisions.
- Bets over requirements: track unresolved product and architecture questions explicitly.
- Loops over gates: shorten feedback cycles instead of building long handoff chains.
- Signal over assumption: capture evidence in `docs/evidence/` and update bets from observed results.

## How We Will Use It
- Every meaningful change should tie back to a bet, ADR, or requirement slice.
- Every iteration should leave behind evidence, not just code.
- Advanced scope such as XMPP federation, the Jabber admin dashboards, and two-server validation stays inside the active loop. Sequencing may place it after core chat flows, but it is not optional scope.
