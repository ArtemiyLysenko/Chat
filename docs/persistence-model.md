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
| `users` | `id`, `email`, `username`, `password_hash`, `created_at` | Registered user identity with unique email and username |
| `password_reset_tokens` | `id`, `user_id`, `token_hash`, `expires_at`, `used_at` | One-time password reset flow |
| `user_sessions` | `id`, `user_id`, `created_at`, `last_seen_at`, `expires_at`, `revoked_at`, `user_agent`, `ip_address` | Persistent browser sessions and selective revocation |
| `session_tabs` | `id`, `session_id`, `tab_key`, `connected_at`, `last_activity_at`, `last_ping_at`, `closed_at` | Per-tab activity and presence derivation |

### Contacts And Direct Messaging

| Table | Key columns | Purpose |
| --- | --- | --- |
| `friendship_requests` | `id`, `requester_user_id`, `recipient_user_id`, `message_text`, `status`, `created_at`, `responded_at` | Pending inbound and outbound friend requests |
| `friendships` | `id`, `user_low_id`, `user_high_id`, `created_at` | Active symmetric friendship relation |
| `user_blocks` | `id`, `blocker_user_id`, `blocked_user_id`, `created_at` | Prevents new contact and direct messages |
| `direct_dialogs` | `id`, `user_low_id`, `user_high_id`, `created_at`, `last_message_at` | Stable direct chat identity per user pair |

### Rooms And Moderation

| Table | Key columns | Purpose |
| --- | --- | --- |
| `rooms` | `id`, `owner_user_id`, `name`, `description`, `visibility`, `created_at` | Public or private room definition |
| `room_memberships` | `id`, `room_id`, `user_id`, `role`, `joined_at` | Current room membership and role |
| `room_invites` | `id`, `room_id`, `invited_user_id`, `invited_by_user_id`, `status`, `created_at`, `accepted_at` | Private room invitation workflow |
| `room_bans` | `id`, `room_id`, `user_id`, `banned_by_user_id`, `reason`, `created_at` | Room-level ban list and actor tracking |
| `moderation_audit_events` | `id`, `room_id`, `actor_user_id`, `target_user_id`, `message_id`, `action`, `reason`, `metadata_json`, `created_at` | Auditable room moderation record |

### Messaging And Attachments

| Table | Key columns | Purpose |
| --- | --- | --- |
| `messages` | `id`, `chat_target_type`, `room_id`, `direct_dialog_id`, `author_user_id`, `parent_message_id`, `body_text`, `state`, `created_at`, `edited_at`, `deleted_at` | Shared message model for rooms and direct dialogs |
| `attachments` | `id`, `storage_key`, `original_name`, `media_type`, `size_bytes`, `sha256`, `uploaded_by_user_id`, `chat_target_type`, `room_id`, `direct_dialog_id`, `created_at` | Attachment metadata and chat ownership |
| `message_attachments` | `message_id`, `attachment_id`, `comment_text`, `sort_order` | Attachment linkage and optional user comment |
| `chat_unread_markers` | `id`, `user_id`, `chat_target_type`, `room_id`, `direct_dialog_id`, `last_read_message_id`, `last_read_at` | Per-user unread clearing and badges |

### XMPP And Federation

| Table | Key columns | Purpose |
| --- | --- | --- |
| `xmpp_client_sessions` | `id`, `user_id`, `jid`, `resource`, `status`, `connected_at`, `disconnected_at`, `remote_address`, `server_node` | Current and recent Jabber/XMPP client connections for the admin dashboard |
| `federation_peers` | `id`, `peer_domain`, `status`, `last_connected_at`, `last_error_at`, `config_json` | Known federated server peers and their state |
| `federation_traffic_samples` | `id`, `peer_id`, `sampled_at`, `inbound_messages`, `outbound_messages`, `inbound_stanzas`, `outbound_stanzas`, `error_count` | Traffic statistics for the federation dashboard and validation evidence |

## Integrity Rules
- `users.email` and `users.username` must be globally unique.
- `rooms.name` must be globally unique.
- `friendships` and `direct_dialogs` must be unique per ordered user pair.
- `room_memberships` must be unique per active room and user pair.
- `room_bans` must be unique per active room and user pair.
- `messages` must reference exactly one target: either `room_id` or `direct_dialog_id`.
- Message text must enforce the 3 KB UTF-8 maximum in both API validation and the persistence layer.
- Attachment validation must enforce the 20 MB file limit and 3 MB image limit before bytes are written to storage.
- Direct-dialog writes must verify an active friendship and no active block in either direction.
- Room history and attachment reads must verify active membership and absence from the ban list at read time.

## Required Indexes
- `users(email)` unique and `users(username)` unique
- `user_sessions(user_id, revoked_at, expires_at)`
- `session_tabs(session_id, tab_key)` unique and `session_tabs(last_ping_at)`
- `rooms(visibility, name)`
- `room_memberships(room_id, user_id)` unique
- `room_bans(room_id, user_id)` unique
- `friendships(user_low_id, user_high_id)` unique
- `user_blocks(blocker_user_id, blocked_user_id)` unique
- `direct_dialogs(user_low_id, user_high_id)` unique
- `messages(room_id, created_at, id)` for room history
- `messages(direct_dialog_id, created_at, id)` for direct-dialog history
- `chat_unread_markers(user_id, chat_target_type, room_id, direct_dialog_id)`
- `attachments(room_id, created_at)` and `attachments(direct_dialog_id, created_at)`
- `xmpp_client_sessions(user_id, status, connected_at)`
- `federation_peers(peer_domain)` unique
- `federation_traffic_samples(peer_id, sampled_at)`

## Deletion And Retention Rules
- Deleting a room hard-deletes the room, its memberships, bans, invites, messages, unread markers, moderation events, attachment metadata, and filesystem blobs.
- Deleting an account invalidates sessions, removes memberships in other rooms, and deletes rooms owned by that user as required by the brief.
- The brief does not specify whether messages by a deleted account outside owned rooms must be removed, anonymized, or preserved with a tombstone identity. This remains an explicit product and architecture bet before account deletion is implemented.
- Friendship removal preserves direct-dialog history but prevents new direct messages until friendship is re-established.
- User blocks preserve direct-dialog history but deny new direct messages and new contact requests.
