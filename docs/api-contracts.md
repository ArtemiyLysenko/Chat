# API Contracts

This document defines the MVP external contracts that implementation work should target.
It is intentionally specific enough to remove architectural ambiguity while still leaving controller and package structure to implementation.

## Contract Principles
- Same-origin session cookies authenticate both HTTP requests and the WebSocket handshake.
- HTTP carries commands, queries, uploads, and downloads.
- WebSocket carries server fan-out events and client tab-activity signals.
- All history, membership, block, and attachment permissions are checked against current database state at request time.
- History uses cursor pagination. The client never requests message offsets.
- XMPP/Jabber interoperability is mandatory and must map onto the same core chat model rather than creating a second messaging domain.

## Shared Types

| Type | Values or shape | Notes |
| --- | --- | --- |
| `ChatTargetRef` | `{ type: ROOM | DIRECT, id: UUID }` | Canonical chat reference across HTTP and WebSocket |
| `RoomAccessLevel` | `FULL`, `INVITED_PREVIEW` | `INVITED_PREVIEW` exposes only the room name and owner for invited private-room users before join |
| `PresenceState` | `ONLINE`, `AFK`, `OFFLINE` | Derived from active tabs |
| `MembershipRole` | `OWNER`, `ADMIN`, `MEMBER` | Room-only role model |
| `MessageState` | `ACTIVE`, `EDITED`, `DELETED` | Shared message lifecycle enum used by the Milestone 4.2 HTTP messaging surface |
| `ModerationAction` | `MEMBER_REMOVED`, `MEMBER_BANNED`, `MEMBER_UNBANNED`, `ADMIN_GRANTED`, `ADMIN_REVOKED`, `MESSAGE_DELETED`, `ROOM_DELETED` | Minimum audit vocabulary |
| `UnreadMarker` | `{ chat, lastReadMessageId, updatedAt }` | One marker per user and chat |
| `SessionSummary` | `{ id, current, createdAt, lastSeenAt, userAgent, ipAddress }` | Returned by active-session APIs |
| `JabberConnectionStatus` | `CONNECTED`, `AUTHENTICATING`, `DISCONNECTED`, `ERROR` | Used by the admin connection dashboard |
| `FederationPeerStatus` | `UP`, `DEGRADED`, `DOWN` | Used by the federation admin view |
| `FederationTrafficSnapshot` | `{ peerDomain, inboundMessages, outboundMessages, inboundStanzas, outboundStanzas, errorCount, sampledAt }` | Minimum statistics payload for federation insight |

## HTTP Contracts

### Identity And Sessions

| Method | Path | Purpose | Notes |
| --- | --- | --- | --- |
| `POST` | `/api/auth/register` | Register a new user | Returns `201` plus a user summary; unique email and username, password hash stored server-side |
| `POST` | `/api/auth/login` | Start a persistent browser session | Returns `200`, a `CHAT_SESSION` cookie, and a `LoginSession` payload |
| `POST` | `/api/auth/logout` | End the current session | Returns `204`, clears the current session cookie, and must not affect other sessions |
| `GET` | `/api/sessions` | List active sessions | Returns `200` with `SessionSummary[]`, including current session flag, user agent, and IP |
| `DELETE` | `/api/sessions/{sessionId}` | Revoke a selected session | Returns `204`; must support revoking non-current sessions and clear the cookie if the caller revokes the current session |
| `POST` | `/api/auth/password/change` | Change password for current user | Returns `204`, requires current authenticated session, keeps that session active, and revokes all other sessions |
| `POST` | `/api/auth/password/reset-requests` | Request a password reset token | Returns `202` even for unknown email; local and test profiles log the raw reset URL once |
| `POST` | `/api/auth/password/reset` | Consume password reset token | Returns `204`, invalidates outstanding reset tokens, and revokes all sessions after success |
| `DELETE` | `/api/account` | Delete current account | Returns `204`, clears the current session cookie, tombstones the identity, deletes owned rooms, removes memberships in non-owned rooms, clears room invites created by or for the user, removes bans targeting the user, removes friend requests, friendships, and user blocks involving the deleted user, preserves `direct_dialogs` rows for later history linkage, and leaves message or attachment cleanup to later milestones |

### Browser UI Routes

| Route | Audience | Purpose |
| --- | --- | --- |
| `/` | unauthenticated by default | Entry surface for the Milestone 1 auth flows |
| `/login` | unauthenticated only | Static sign-in page |
| `/register` | unauthenticated only | Static registration page |
| `/password-reset/request` | unauthenticated only | Static password reset request page |
| `/password-reset/consume` | unauthenticated only | Static password reset consume page |
| `/app` | authenticated only | Static authenticated shell page |
| `/app/contacts` | authenticated only | Static contacts workspace for friendship management, direct-dialog entry, and HTTP-backed unread badges |
| `/app/direct-dialogs/{userId}` | authenticated only | Dedicated direct-dialog workspace that ensures or reuses the stable direct-dialog identity for one eligible friend pair and renders the HTTP-backed chat timeline |
| `/app/sessions` | authenticated only | Static active-session management page |

### Rooms And Moderation

| Method | Path | Purpose | Notes |
| --- | --- | --- | --- |
| `GET` | `/api/rooms` | List rooms for the caller | Supports `scope=joined` for the sidebar list and `scope=catalog` for public room discovery. Joined-room rows now include an HTTP-backed `unreadCount` for badge rendering |
| `POST` | `/api/rooms` | Create room | Unique room name, public or private visibility |
| `GET` | `/api/rooms/{roomId}` | Load room details and caller membership | Public rooms expose full details to authenticated, non-banned users. Private rooms expose full details only to members. Invited private-room users may load only `INVITED_PREVIEW`, which includes the room name and owner. Hidden or banned rooms must not leak information |
| `POST` | `/api/rooms/{roomId}/join` | Join a room | This is the single membership-entry path for both public rooms and accepted private invites. It must fail if the caller is banned or lacks a private-room invite |
| `POST` | `/api/rooms/{roomId}/leave` | Leave a room | Owner must be denied and instructed to delete the room instead |
| `POST` | `/api/rooms/{roomId}/invites` | Invite a user to a private room | Admin or owner only |
| `PUT` | `/api/rooms/{roomId}/admins/{userId}` | Grant admin role | Owner only |
| `DELETE` | `/api/rooms/{roomId}/admins/{userId}` | Revoke admin role | Owner only |
| `DELETE` | `/api/rooms/{roomId}/members/{userId}` | Remove a member from the room | Removing a regular member is remove-only. Removing an admin removes membership, creates a ban, and records both moderation events |
| `GET` | `/api/rooms/{roomId}/bans` | Inspect room bans | Includes who placed each ban |
| `PUT` | `/api/rooms/{roomId}/bans/{userId}` | Ban a user | Records actor and reason, removes any current membership, and clears any pending invite |
| `DELETE` | `/api/rooms/{roomId}/bans/{userId}` | Unban a user | Admin or owner only |
| `DELETE` | `/api/rooms/{roomId}` | Delete room | Owner only; hard-deletes room-owned state including memberships, invites, bans, and moderation records. Later milestones extend this cascade to messages and attachments |

### Contacts And Direct Messaging

Milestone 3 is complete across slices 3.1 through 3.3.
Milestone 4.2 builds on that foundation with unread-badge rendering and the HTTP-backed direct-dialog workspace.

| Method | Path | Purpose | Notes |
| --- | --- | --- | --- |
| `GET` | `/api/contacts` | List friends, pending requests, and block state | Returns `viewerUserId`, `friends`, `inboundPendingRequests`, `outboundPendingRequests`, and `blockedUsers`. Each friend row includes `directDialogId` when a stable dialog exists plus an HTTP-backed `unreadCount` for that direct dialog |
| `POST` | `/api/friend-requests` | Create friend request by username or user id | Requires exactly one of `userId` or `username`, supports optional `messageText`, auto-accepts the opposite-direction pending request when one already exists, and denies new requests while either user has blocked the other |
| `POST` | `/api/friend-requests/{requestId}/accept` | Accept friend request | Recipient-only action that creates the friendship relation |
| `POST` | `/api/friend-requests/{requestId}/reject` | Reject friend request | Recipient-only action that marks the request rejected and keeps no direct-dialog eligibility |
| `DELETE` | `/api/contacts/{userId}` | Remove a friend | Removes the active friendship only; it does not create a block and it does not delete an existing direct dialog |
| `PUT` | `/api/blocks/{userId}` | Block a user | Rejects self-blocks, removes any active friendship immediately, retires any pending friend request between the pair, preserves any existing direct dialog, and makes the pair ineligible for new direct messages while the block is active |
| `DELETE` | `/api/blocks/{userId}` | Remove a block | Removes only the caller's directional block; it does not restore friendship automatically |
| `POST` | `/api/direct-dialogs/{userId}` | Ensure or fetch the direct dialog with a friend | Returns `201` when the dialog row is created and `200` when an existing row is reused. The pair must currently have an active friendship and no block in either direction. Existing dialog ids stay stable after later friendship removal, block, unblock, or account deletion |

### Messaging, History, And Read State

| Method | Path | Purpose | Notes |
| --- | --- | --- | --- |
| `GET` | `/api/chats/{chatType}/{chatId}/messages` | Read a page of message history | Implemented through Milestone 4.2. Supports `before` as the oldest visible message id and `limit` with a default of `50` and a max of `100`; returns chronological `items` plus `nextBeforeMessageId` |
| `POST` | `/api/chats/{chatType}/{chatId}/messages` | Send a new message or reply | Implemented in Milestone 4.2. Request body is `{ bodyText, parentMessageId? }`; UTF-8 text up to 3 KB; `parentMessageId` must reference a message in the same room or direct dialog |
| `PATCH` | `/api/messages/{messageId}` | Edit own message | Implemented in Milestone 4.2. Author-only; preserves the row and transitions message state to `EDITED` |
| `DELETE` | `/api/messages/{messageId}` | Delete message | Implemented in Milestone 4.2. Direct-dialog deletes are author-only. Room deletes are allowed for the author or a room `OWNER` or `ADMIN`. The row is preserved and the returned message state becomes `DELETED` |
| `POST` | `/api/chats/{chatType}/{chatId}/read-markers` | Advance caller read marker | Implemented in Milestone 4.1 and reused in Milestone 4.2. Request body is `{ lastReadMessageId }`; the marker moves only forward and is the source of truth for unread-badge clearing |

Milestone 4.2 response shapes:

- `POST /api/chats/{chatType}/{chatId}/messages`

```json
{
  "id": "uuid",
  "chat": {
    "type": "ROOM",
    "id": "uuid"
  },
  "author": {
    "id": "uuid",
    "username": "captain",
    "displayName": "Captain",
    "deleted": false
  },
  "bodyText": "Hello room",
  "state": "ACTIVE",
  "createdAt": "2026-04-21T12:00:00Z",
  "editedAt": null,
  "replyTo": null,
  "attachments": []
}
```

- `GET /api/chats/{chatType}/{chatId}/messages`

```json
{
  "chat": {
    "type": "ROOM",
    "id": "uuid"
  },
  "items": [],
  "nextBeforeMessageId": "uuid or null"
}
```

- Edited, deleted, or reply messages reuse the same `ChatMessage` shape

```json
{
  "id": "uuid",
  "chat": {
    "type": "ROOM",
    "id": "uuid"
  },
  "author": {
    "id": "uuid",
    "username": "captain",
    "displayName": "Captain",
    "deleted": false
  },
  "bodyText": "Edited reply text",
  "state": "EDITED",
  "createdAt": "2026-04-21T12:00:00Z",
  "editedAt": "2026-04-21T12:05:00Z",
  "replyTo": {
    "id": "uuid",
    "author": {
      "id": "uuid",
      "username": "scout",
      "displayName": "Scout",
      "deleted": true
    },
    "bodyText": "Deleted message",
    "state": "DELETED"
  },
  "attachments": []
}
```

### Attachments

| Method | Path | Purpose | Notes |
| --- | --- | --- | --- |
| `POST` | `/api/chats/{chatType}/{chatId}/attachments` | Upload file or image and optional comment | Multipart form with required `file`, optional `commentText`, and reserved `messageId`; 20 MB file cap and 3 MB image cap |
| `GET` | `/api/attachments/{attachmentId}` | Read attachment metadata | Same authorization as download |
| `GET` | `/api/attachments/{attachmentId}/download` | Download binary content | Authorization checked at request time |

Milestone 5.1 contract notes:
- `messageId` is reserved for a later attach-to-existing-message flow. The current backend returns `400` with `attachments.message_binding_unsupported` when it is provided.
- `ChatMessage` now includes `attachments`, ordered by `sortOrder`. Text-only messages return an empty array.

`AttachmentDescriptor` remains the metadata response shape for upload and metadata reads:

```json
{
  "id": "uuid",
  "storageKey": "uploads/uuid",
  "originalName": "notes.txt",
  "mediaType": "text/plain",
  "sizeBytes": 128
}
```

### Jabber Administration

| Method | Path | Purpose | Notes |
| --- | --- | --- | --- |
| `GET` | `/api/admin/jabber/connections` | Show the Jabber/XMPP connection dashboard | Admin-only, includes current connection status and user association |
| `GET` | `/api/admin/jabber/federation/peers` | Show current federation peer status | Admin-only, includes peer health and last-seen state |
| `GET` | `/api/admin/jabber/federation/traffic` | Show federation traffic statistics | Admin-only, supports the required federation traffic info screen |

- Temporary authorization baseline through Milestone 1: `/api/admin/**` is denied for all authenticated users until the real admin authorization model is implemented.

## XMPP Interoperability Contract

- The XMPP client-facing contract is the chosen standards-compliant Jabber/XMPP library and support level, not a custom HTTP API.
- At minimum, the integration must let a Jabber/XMPP client authenticate, participate in the supported chat scope, and exchange messages across federated servers.
- The exact supported stanza set and federation depth remain an implementation bet until the XMPP milestone closes, but the feature itself is mandatory acceptance scope.

## WebSocket Contract

Milestone 4.3 implements the authenticated backend `/ws` channel.
Milestone 4.4 now wires the room shell, contacts workspace, and direct-dialog browser UI to these events and adds reconnect behavior.

- Path: `/ws`
- Authentication: session cookie from the same origin login flow
- Purpose: fan out low-latency state changes to authorized sessions and receive tab activity heartbeats

### Event Envelope

```json
{
  "eventId": "uuid",
  "type": "message.created",
  "occurredAt": "2026-04-20T12:00:00Z",
  "chat": {
    "type": "ROOM",
    "id": "uuid"
  },
  "payload": {}
}
```

- `chat` is present for chat-scoped events and `null` for non-chat events such as `session.revoked`.

### Canonical Server Event Types
- `message.created`
- `message.updated`
- `message.deleted`
- `unread.updated`
- `presence.updated`
- `room.updated`
- `room.membership.updated`
- `friendship.updated`
- `moderation.recorded`
- `session.revoked`
- `jabber.connection.updated`
- `federation.peer.updated`
- `federation.traffic.updated`

Milestone 4.3 currently fans out:
- `message.created`
  - payload: `ChatMessage`
- `message.updated`
  - payload: `ChatMessage`
- `message.deleted`
  - payload: `ChatMessage`
- `unread.updated`
  - payload: `{ unreadCount, lastReadMessageId, updatedAt }`
- `session.revoked`
  - payload: `{ sessionId }`

### Client Control Messages
- `tab.activity`
  Sends the stable tab key plus the latest activity timestamp.
- `tab.closed`
  Best-effort signal that a browser tab is closing.
- `subscription.resume`
  Optional reconnect hint carrying the last processed event id.

Current backend behavior:
- Milestone 4.3 accepts `subscription.resume` as a forward-compatible reconnect hint without server-side replay.
- Milestone 4.4 browser clients now send `subscription.resume` with the last seen event id and refresh HTTP-backed room, contact, or direct-dialog state after reconnect.
- `tab.activity` and `tab.closed` stay reserved for Milestone 6 presence handling.

## History And Access Rules
- Initial history load returns the newest page for the selected chat.
- The response order within a page is chronological to keep rendering simple.
- In Milestone 4.1, `before` is the oldest message id already shown. The next page loads older messages.
- Milestone 4.2 keeps the same `before` semantics and renders merged pages in chronological order.
- Room history reads require current room membership. Banned or removed users must receive authorization failure immediately.
- Direct-dialog history reads require that the caller is one of the dialog participants. Existing history stays readable after friendship removal or block, but new sends are denied while the pair is ineligible.
- Reply targets must stay inside the same room or direct dialog as the new message. The current 4.2 implementation allows replies to deleted messages as long as the target stays in the same chat.
- Editing a deleted message is rejected. Deleting a message never removes the row; the API returns `state: DELETED` and the UI renders a deleted marker.
- Tombstoned authors are rendered from the preserved `users` row through the normal message contract with `author.deleted = true`.
- Attachment reads use the same room or direct-dialog authorization path as message history. Authorization is never based only on upload ownership.
