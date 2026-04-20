# ADR 0003: Conversation Model And Realtime Contract

## Status
Accepted

## Context
The brief requires rooms, direct messaging, history pagination, unread indicators, attachment access control, and low-latency updates.
Without a clear contract, implementation could diverge between room chat and personal dialogs or overload WebSocket with responsibilities better handled elsewhere.

## Decision
For the MVP:
- room chat and direct dialogs share a common logical chat contract identified as `ChatTargetRef { type, id }`
- message writes, edits, deletes, read-marker updates, room membership changes, and attachment uploads are handled through HTTP endpoints
- WebSocket is the authenticated server-push channel for chat, unread, presence, and moderation events, plus a narrow set of client control messages for tab activity
- message history uses cursor pagination from day one
- attachment authorization is evaluated on every metadata or download request against the caller's current room or direct-dialog access

## Why
- The brief says room chats and personal dialogs should feel like the same messaging experience.
- HTTP is the pragmatic write path for a classic web application, especially for multipart uploads and auditable controller behavior.
- A single event envelope keeps the live-update model consistent for all features that need fan-out.
- Cursor pagination is safer than offsets for large histories and concurrent message writes.

## Consequences
- The implementation should expose one shared message and unread model across room and direct chat features, even if the persistence model keeps separate room and dialog identifiers.
- WebSocket authorization must filter events so clients receive only updates they can still access.
- Message deletion should be modeled as a state transition to preserve history semantics and edited or deleted markers in the UI.
- Blocking a user or losing room access must immediately stop new sends and future downloads, even if the caller still has an older page open.
