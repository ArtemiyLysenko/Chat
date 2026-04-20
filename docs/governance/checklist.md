# Governance Checklist

Use this before accepting a milestone as complete.

## Alignment
- Does the change clearly serve a requirement from the brief?
- Is the requirement slice explicitly named in the milestone evidence?
- Does it preserve the classic web chat experience?
- Does it keep `docker compose up` from the repository root as the primary local entry point?

## Architecture
- Are architecture-impacting decisions captured in `docs/architecture.md`, an ADR, or the bet register?
- Are API or persistence contract changes reflected in `docs/api-contracts.md` or `docs/persistence-model.md`?
- If the brief is silent on a behavior, is the chosen assumption explicit together with one viable alternative?

## Quality
- Is there fresh validation evidence for the change?
- Are the main failure modes documented or covered by tests?
- Are latency, history, or concurrency targets addressed when the slice touches them?
- If the slice touches Jabber or federation, is there evidence for peer visibility, traffic stats, or two-server behavior?
- Are security-sensitive flows handled with current best practice for the chosen stack?

## Scope Control
- Did this change avoid inventing scope beyond the brief while still preserving the mandatory advanced Jabber and federation requirements?
- Were new assumptions recorded as bets or ADRs?
- If the stack direction changed, was an ADR added?

## Operational Readiness
- Can another agent or human understand the current state from the repo docs?
- Are setup steps, environment variables, and data/storage expectations documented?
- Is the next milestone already clear from `docs/mvp-delivery-plan.md` or a narrower follow-up note?
- Is there a clear next bet after this milestone?

## Exit Package
- Is there at least one automated proof where automation is practical?
- Is there concise manual evidence for the user-visible flow where automation is not yet practical?
- Are logs, screenshots, or benchmark notes stored under `docs/evidence/` with a date and milestone label?
