# AGENTS.md

## Mission
Build a classic web chat application for the AI Herders Jam hackathon with AI-first delivery. Human input should focus on governance, product decisions, and final review rather than routine implementation.

## Source Of Truth
- Hackathon brief: `2026_04_18_AI_herders_jam_-_requirements_v3 1.docx`
  The brief is the authoritative acceptance source for product behavior.
- ADLC source: https://www.adlc.io/
- Repository planning docs under `docs/`
  These are derived working artifacts and must not narrow or contradict the brief.

## Delivery Mode
- Follow ADLC as an operating loop, not a sequential SDLC pipeline.
- Keep six modes active in parallel: `Intent`, `Generate`, `Validate`, `Govern`, `Deploy`, `Observe`.
- Track work as bets with explicit signals and decision deadlines.
- Prefer pragmatic progress over ceremony. Small changes do not need heavy process.

## Initial Product Scope
- Required: authentication, public/private rooms, direct messaging, contacts, moderation, attachments, presence, persistent history, active sessions.
- Required advanced scope: Jabber/XMPP client connectivity, inter-server federation, an admin connection dashboard, federation traffic statistics, and two-server federation load-test evidence.

## Working Rules
- Use facts from the brief and repo docs. If a fact is missing, state the assumption and record it in `docs/bet-register.md` or an ADR.
- Keep the repository runnable by `docker compose up` from the root once implementation starts.
- Prefer one coherent stack across frontend, backend, realtime, persistence, and tests.
- Preserve classic web chat UX over novelty UI.
- Design for up to 300 concurrent users without premature infrastructure complexity.
- The governed application stack is Java 25, Gradle, Spring Boot, and PostgreSQL. Do not change that without an explicit replacement ADR.

## Governed Technical Direction
- Application runtime: Java 25
- Build system: Gradle 9.4.1
- Backend framework: Spring Boot 4.0.5
- Primary persistence: PostgreSQL
- Initial UI delivery: Spring Boot-served web UI and static assets
- Realtime delivery: Spring WebSocket support
- File storage: local filesystem under `storage/`
- Local orchestration: Docker Compose

## Open Design Space
- Whether the classic web chat UI remains Spring Boot-served or later moves to a dedicated frontend module.

## Required Artifacts
- `docs/bet-register.md` for active bets and open questions
- `docs/architecture.md` for the current system shape
- `docs/governance/checklist.md` for release and review decisions
- `docs/adrs/` for decisions with meaningful tradeoffs
- `docs/evidence/` for screenshots, test notes, and benchmark evidence

## Definition Of Progress
- A change is only considered complete when the implementation, validation evidence, and governing decision are all captured.
- Validation must include the simplest proof that materially reduces risk: tests, manual flows, screenshots, load probes, or log evidence.
