# Milestone Implementation Plans

These documents are the execution layer under `docs/mvp-delivery-plan.md`.
Use them as the decision-complete spec for each delivery slice.

- [Milestone 1: Identity, Password Lifecycle, And Active Sessions](01-identity-sessions.md)
- [Milestone 2: Room Catalog, Membership, And Moderation](02-rooms-moderation.md)
- [Milestone 3: Friendships, Blocks, And Direct-Dialog Eligibility](03-contacts-direct-dialogs.md)
- [Milestone 4: Realtime Messaging, Unread Markers, And History Pagination](04-messaging-history-realtime.md)
- [Milestone 5: Attachments And Access Revocation](05-attachments-access-control.md)
- [Milestone 6: Presence And Multi-Tab Behavior](06-presence-multi-tab.md)
- [Milestone 7: Jabber/XMPP Client Support And Inter-Server Federation](07-xmpp-federation.md)
- [Milestone 8: Jabber Admin Dashboards And Federation Load Validation](08-admin-dashboards-load-validation.md)

## Shared Rules
- `docs/mvp-delivery-plan.md` remains the master sequence and dependency map.
- `docs/api-contracts.md`, `docs/persistence-model.md`, and `docs/architecture.md` remain the source of truth for shared contracts and architecture unless a milestone doc explicitly updates them.
- Every milestone must leave behind:
  - updated docs for any contract, persistence, or architecture change
  - one dated evidence note under `docs/evidence/`
  - a green `./gradlew test`
  - a bet-register or ADR update when the slice resolves a bet or changes architecture
- Keep the app as one Spring Boot deployable, use static Spring Boot-served UI, and do not introduce Redis, STOMP, Node build tooling, or a separate frontend unless a later ADR explicitly changes that direction.
