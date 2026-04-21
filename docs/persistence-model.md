# Persistence Model

This document defines the initial logical data model for the MVP.
PostgreSQL is the authoritative store for durable application state. The filesystem stores attachment bytes only.

## Persistence Principles
- Use PostgreSQL for identities, sessions, chat state, permissions, unread markers, and moderation audit.
- Use local filesystem storage under `storage/` for binaries, referenced by immutable storage keys in the database.
- Prefer hard deletes for entities whose removal is explicitly required by the brief, such as room deletion.
- Prefer message state transitions over physical message deletion in normal operations so history can show deleted markers.
- Make permission-critical joins explicit and indexable from the start.
- Persist enough Jabber and federation state to drive the required admin dashboards and federation validation evidence.

## Logical Tables

### Identity And Sessions

| Table | Key columns | Purpose |
| --- | --- | --- |
| `users` | `id`, `email`, `username`, `display_name`, `password_hash`, `deleted_at`, `created_at` | Registered user identity with case-insensitive unique email and username, plus tombstone preservation fields |
| `password_reset_tokens` | `id`, `user_id`, `token_hash`, `expires_at`, `used_at` | One-time password reset flow with hashed token storage only |
| `user_sessions` | `id`, `user_id`, `created_at`, `last_seen_at`, `expires_at`, `revoked_at`, `user_agent`, `ip_address` | Persistent browser sessions and selective revocation |
| `session_tabs` | `id`, `session_id`, `tab_key`, `connected_at`, `last_activity_at`, `last_ping_at`, `closed_at` | Per-tab activity and presence derivation |

### Contacts And Direct Messaging

Milestone 3 now implements `friendship_requests`, `friendships`, `user_blocks`, and `direct_dialogs`.

| Table | Key columns | Purpose |
| --- | --- | --- |
| `friendship_requests` | `id`, `requester_user_id`, `recipient_user_id`, `message_text`, `status`, `created_at`, `responded_at` | Pending inbound and outbound friend requests |
| `friendships` | `id`, `user_low_id`, `user_high_id`, `created_at` | Active symmetric friendship relation |
| `user_blocks` | `id`, `blocker_user_id`, `blocked_user_id`, `created_at` | Active directional user blocks that deny new friend requests and new direct messages while active |
| `direct_dialogs` | `id`, `user_low_id`, `user_high_id`, `created_at` | Stable direct chat identity per ordered user pair, preserved across later friendship removal, block, unblock, and account deletion |

### Rooms And Moderation

| Table | Key columns | Purpose |
| --- | --- | --- |
| `rooms` | `id`, `owner_user_id`, `name`, `description`, `visibility`, `created_at` | Public or private room definition |
| `room_memberships` | `id`, `room_id`, `user_id`, `role`, `joined_at` | Current room membership and role |
| `room_invites` | `id`, `room_id`, `invited_user_id`, `invited_by_user_id`, `status`, `created_at`, `accepted_at` | Private room invitation workflow, including the pending invite state used to authorize the private-room pre-join preview and the single join path |
| `room_bans` | `id`, `room_id`, `user_id`, `banned_by_user_id`, `reason`, `created_at` | Room-level ban list and actor tracking; member removal of an admin promotes into this table as part of the same moderation flow |
| `moderation_audit_events` | `id`, `room_id`, `actor_user_id`, `target_user_id`, `message_id`, `action`, `reason`, `metadata_json`, `created_at` | Auditable room moderation record while the room exists; room deletion hard-deletes this room-owned state with the room |

### Messaging And Attachments

| Table | Key columns | Purpose |
| --- | --- | --- |
| `messages` | `id`, `room_id`, `direct_dialog_id`, `parent_message_id`, `author_user_id`, `body_text`, `state`, `created_at`, `edited_at`, `deleted_at` | Milestone 4.2 shared message model for rooms and direct dialogs. Reply linkage, edit timestamps, and delete tombstones preserve one row per message mutation |
| `attachments` | `id`, `storage_key`, `original_name`, `media_type`, `size_bytes`, `sha256`, `uploaded_by_user_id`, `chat_target_type`, `room_id`, `direct_dialog_id`, `created_at` | Attachment metadata and chat ownership |
| `message_attachments` | `message_id`, `attachment_id`, `comment_text`, `sort_order` | Attachment linkage and optional user comment |
| `chat_unread_markers` | `id`, `user_id`, `room_id`, `direct_dialog_id`, `last_read_message_id`, `updated_at` | Per-user unread clearing and unread-badge foundation. Each row references exactly one room or direct dialog target and advances only forward |

### XMPP And Federation

| Table | Key columns | Purpose |
| --- | --- | --- |
| `xmpp_client_sessions` | `id`, `user_id`, `jid`, `resource`, `status`, `connected_at`, `disconnected_at`, `remote_address`, `server_node` | Current and recent Jabber/XMPP client connections for the admin dashboard |
| `federation_peers` | `id`, `peer_domain`, `status`, `last_connected_at`, `last_error_at`, `config_json` | Known federated server peers and their state |
| `federation_traffic_samples` | `id`, `peer_id`, `sampled_at`, `inbound_messages`, `outbound_messages`, `inbound_stanzas`, `outbound_stanzas`, `error_count` | Traffic statistics for the federation dashboard and validation evidence |

## Integrity Rules
- `users.email` and `users.username` must be globally unique.
- `rooms.name` must be globally unique.
- `friendship_requests` must reject duplicate pending requests in the same direction.
- `friendships` must be unique per ordered user pair.
- `user_blocks` must be unique per blocker and blocked direction pair.
- `direct_dialogs` must be unique per ordered user pair.
- `room_memberships` must be unique per active room and user pair.
- `room_bans` must be unique per active room and user pair.
- `messages` must reference exactly one target: either `room_id` or `direct_dialog_id`.
- `messages.parent_message_id`, when present, must reference a message in the same room or direct dialog target.
- `chat_unread_markers` must reference exactly one target: either `room_id` or `direct_dialog_id`.
- Message text must enforce the 3 KB UTF-8 maximum in both API validation and the persistence layer.
- Message edits and deletes must preserve the original message row and advance `state`, `edited_at`, or `deleted_at` instead of removing the row.
- Attachment validation must enforce the 20 MB file limit and 3 MB image limit before bytes are written to storage.
- Direct-dialog writes must verify an active friendship and no active block in either direction.
- Room history and attachment reads must verify active membership and absence from the ban list at read time.

## Required Indexes
- `lower(users.email)` unique and `lower(users.username)` unique
- `password_reset_tokens(token_hash)` unique
- `user_sessions(user_id, revoked_at, expires_at)`
- `session_tabs(session_id, tab_key)` unique and `session_tabs(last_ping_at)`
- `friendship_requests(requester_user_id, status, created_at)`
- `friendship_requests(recipient_user_id, status, created_at)`
- partial unique `friendship_requests(requester_user_id, recipient_user_id)` where status is `PENDING`
- `friendships(user_low_id, user_high_id)` unique
- `rooms(visibility, name)`
- `room_memberships(room_id, user_id)` unique
- `room_bans(room_id, user_id)` unique
- `user_blocks(blocker_user_id, blocked_user_id)` unique
- `direct_dialogs(user_low_id, user_high_id)` unique
- partial `messages(room_id, created_at desc, id desc)` where `room_id` is not null for room history
- partial `messages(direct_dialog_id, created_at desc, id desc)` where `direct_dialog_id` is not null for direct-dialog history
- partial unique `chat_unread_markers(user_id, room_id)` where `room_id` is not null
- partial unique `chat_unread_markers(user_id, direct_dialog_id)` where `direct_dialog_id` is not null
- `attachments(room_id, created_at)` and `attachments(direct_dialog_id, created_at)`
- `xmpp_client_sessions(user_id, status, connected_at)`
- `federation_peers(peer_domain)` unique
- `federation_traffic_samples(peer_id, sampled_at)`

## Deletion And Retention Rules
- The current implementation hard-deletes a room together with its memberships, bans, invites, moderation events, messages, and unread markers. Later milestones extend the same room-owned cleanup to attachments and filesystem blobs.
- Milestone 1 account deletion invalidates sessions, clears credentials, replaces email and username with unique tombstone values, and preserves historical non-owned messages through a tombstoned `users` row.
- Account deletion now uses `AccountDeletionImpactPort` to delete rooms owned by the deleted user, remove their memberships from other rooms, remove invites created by or for them, remove bans targeting them, remove friendship requests, friendships, and user blocks involving them, and preserve `direct_dialogs` rows for future history linkage.
- Tombstoned `users` rows remain in place so moderation audit records and surviving ban-actor references in non-deleted rooms can still resolve historical actors.
- Tombstoning clears login ability and personal identifiers while keeping a stable row for later message authorship joins, reply previews, and UI rendering.
- Milestone 4.2 message deletes are logical deletes only. The `messages` row remains available for history ordering, reply previews, and deleted markers.
- Friendship removal preserves direct-dialog history but prevents new direct messages until friendship is re-established.
- User blocks preserve direct-dialog history, deny new direct messages and new contact requests, and immediately retire any pending friendship request between the pair into rejected state.
