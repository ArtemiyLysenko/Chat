# Milestone 5.1 Evidence

- Date: 2026-04-21
- Slice: Milestone 5.1 attachment backend, metadata and blob persistence, authorized metadata reads and downloads, and filesystem storage

## What Changed
- Added the `attachments` feature module service layer for upload, metadata read, and binary download authorization.
- Added forward-only Flyway migrations for `attachments` and `message_attachments`.
- Added the JPA persistence adapter for attachment metadata and message linkage, plus attachment-aware message loading for history reads and websocket payload reuse.
- Added the real filesystem storage adapter under `storage/uploads/<attachment-id>` with read and delete support and rollback cleanup for failed transactions.
- Added HTTP endpoints for multipart upload, metadata read, and binary download.
- Extended `ChatMessage` payloads to expose ordered attachment metadata so later Milestone 5.2 browser work can render uploaded files without another contract change.

## Contract Decisions Captured In This Slice
- Uploads stay message-bound and chat-bound.
- The multipart `messageId` field remains reserved in Milestone 5.1.
  - Current behavior: non-null `messageId` returns `400` with `attachments.message_binding_unsupported`.
  - Reason: this backend slice creates a new attachment-bearing message per upload and defers attach-to-existing-message UX until or unless a later flow needs it.
- Attachment comment or default message text is kept at the same 3 KB UTF-8 ceiling as regular message bodies.

## Automated Verification
- Targeted attachment slice:
  - `./gradlew :modules:features:attachments:test --tests '*DefaultAttachmentsServiceTests' :modules:adapters:persistence-jpa:test --tests '*AttachmentsRepositoryIntegrationTests' :apps:api:test --tests '*AttachmentsHttpIntegrationTests' --no-daemon`
  - result: passed
- Full regression suite after the final architecture and test-fixture fixes:
  - `./gradlew test --no-daemon`
  - result: passed

## Proven Behaviors
- Upload validation rejects empty uploads, files above 20 MB, and images above 3 MB before blob persistence.
- Upload creates a new attachment-bearing message, persists attachment metadata, links the attachment to the message, stores the blob on disk, and emits the standard `message.created` event payload with the attachment metadata included.
- `GET /api/attachments/{attachmentId}` and `GET /api/attachments/{attachmentId}/download` both re-check current room or direct-dialog authorization at request time.
- A removed room member immediately loses both metadata and download access to previously uploaded room attachments.
- Download responses return the stored binary bytes with the saved media type and attachment filename.

## Reviewer Pass
- Correctness: verified upload, metadata, download, message linkage, and attachment-aware message history loading with unit, JPA, and HTTP integration tests.
- Regressions: full `./gradlew test --no-daemon` passed after updating the attachment bridge to respect the repo’s cross-feature API-only architecture rule.
- Edge cases: covered file-size limits, image-size limits, current-membership enforcement, direct-send eligibility enforcement, and storage cleanup on rollback.
- Contract alignment: updated `docs/api-contracts.md` to document the live attachment endpoints, `AttachmentDescriptor`, reserved `messageId`, and the new `attachments` field on `ChatMessage`.
- Testing gap: browser upload, paste-image, timeline rendering, and room-delete blob cleanup remain intentionally unverified until Milestone 5.2.

## Result Summary
- Milestone 5.1 is implemented and verified.
- The repository now has a real attachment backend with PostgreSQL metadata, filesystem blobs, request-time authorization, and attachment metadata embedded in chat message payloads.
- Milestone 5 stays open until the 5.2 browser integration and cleanup slice is completed.
