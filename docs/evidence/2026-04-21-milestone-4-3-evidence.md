# Milestone 4.3 Evidence

Date: 2026-04-21
Milestone: 4.3

## Requirement Slice
- Raw authenticated `/ws` backend.
- Canonical server event envelope for backend fan-out.
- Server-side fan-out for `message.created`, `message.updated`, `message.deleted`, and `unread.updated`.
- Live WebSocket invalidation when a browser session is revoked.

## Code And Doc Changes Validated
- Added the authenticated `/ws` handler and handshake interceptor in `apps/api`.
- Added backend event publication from the messaging and identity feature services.
- Added a persistence-adapter `ChatAudienceQuery` to route room and direct-dialog fan-out without breaking the architecture rules.
- Updated the Milestone 4, architecture, API-contract, README, and shipped browser-note copy to reflect that the backend `/ws` slice is implemented while browser live wiring remains Milestone 4.4 work.

## Automated Verification
- Targeted backend verification:
  - `./gradlew :apps:api:test --tests 'edu.artemiy.chat.app.websocket.ChatWebSocketIntegrationTests' --tests 'edu.artemiy.chat.app.http.ApplicationHttpWiringTests' --tests 'edu.artemiy.chat.app.http.MessagingHttpIntegrationTests' --no-daemon`
  - Result: passed
- Full regression suite:
  - `./gradlew test --no-daemon`
  - Result: passed

## Backend Event Flow Confirmed
- `ChatWebSocketIntegrationTests.rejectsUnauthenticatedHandshake`
  - Confirmed `/ws` rejects unauthenticated handshakes with HTTP `401`.
- `ChatWebSocketIntegrationTests.fansOutMessageLifecycleAndUnreadUpdatesToAuthorizedRoomSockets`
  - Confirmed an authorized room member receives:
    - `message.created`
    - `unread.updated`
    - `message.updated`
    - `message.deleted`
    - `unread.updated` after advancing the read marker
- `ChatWebSocketIntegrationTests.revokesLiveSocketWhenSessionIsRevoked`
  - Confirmed revoking a live browser session produces `session.revoked` and closes the socket with close code `4401`.

## Review Notes
- Reviewer pass checked:
  - handshake auth uses the same persistent session cookie and session store as HTTP
  - fan-out stays chat-authorized and room or direct-dialog scoped
  - session-revocation handling survives explicit revocation, logout, password reset, password change, and account deletion through the shared identity service path
  - the new audience-query seam stays in the persistence adapter so the feature-module architecture rules remain green

## Known Gaps
- The browser UI still consumes HTTP-only chat updates until Milestone 4.4.
- `subscription.resume` is currently a forward-compatible no-op; reconnect and UI refresh behavior land in Milestone 4.4.

## Status After This Slice
- Milestone 4.3 backend scope is complete and verified.
- Full Milestone 4 remains open until the Milestone 4.4 browser closeout lands.
