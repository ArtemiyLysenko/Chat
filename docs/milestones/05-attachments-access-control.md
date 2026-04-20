# Milestone 5: Attachments And Access Revocation

## Summary
This milestone adds file and image sharing as first-class chat content.
It includes upload, metadata, download, attachment comments, and immediate access revocation when room access changes.

## Deliverables
- Multipart upload for files and images.
- Attachment metadata reads, binary downloads, and optional attachment comments.
- Attachment rendering inside the chat timeline.
- Filesystem-backed blob storage plus database-backed metadata and authorization.

## Out Of Scope
- Image preview generation and thumbnail pipelines.
- Virus scanning or external object storage.
- Standalone attachment library views outside the chat context.

## Implementation Plan
1. Attachments feature module
   - Implement `api`, `application`, and `spi` packages in `modules/features/attachments`.
   - Keep metadata and authorization logic in the feature module.
   - Keep binary storage behind `AttachmentStoragePort`.
2. Database and persistence adapter
   - Add Flyway migrations for `attachments` and `message_attachments`.
   - Implement JPA entities and repositories in `modules/adapters/persistence-jpa/attachments`.
   - Link attachments to chats and messages through immutable storage keys.
3. Filesystem adapter
   - Implement the real storage adapter in `modules/adapters/storage-filesystem`.
   - Store binaries under `storage/uploads/<attachment-id>`.
   - Do not depend on the filesystem name for user-visible filename display.
   - Keep `storage/previews/<attachment-id>` reserved but unused unless preview generation is added later.
4. Validation and authorization
   - Enforce 20 MB file limit and 3 MB image limit before bytes are written.
   - Preserve the original filename in metadata.
   - Re-check current room membership or direct-dialog participation on every metadata read and every download request.
   - If a user loses room access, downloads must fail immediately even if the user uploaded the file.
5. Message integration and UI
   - Implement upload endpoints in `apps/api/src/main/java/edu/artemiy/chat/app/http`.
   - Extend the chat timeline UI from Milestone 4 to show attachment items and comments.
   - Add upload-button and paste-image entry points.
   - Keep uploads bound to chats and messages; do not support unattached drafts in this milestone.
6. Delete and cleanup behavior
   - Room deletion must delete both metadata and blob files.
   - Account deletion does not remove files from rooms the user no longer owns; room ownership still governs hard-delete behavior.

## Contract And Behavior Locks
- Use the attachment endpoints already defined in `docs/api-contracts.md`.
- Keep `AttachmentDescriptor` as the returned metadata shape.
- `POST /api/chats/{chatType}/{chatId}/attachments` uses multipart form data with:
  - one binary part
  - optional `commentText`
  - optional `messageId` when attaching to an existing message flow if needed by the chosen controller design
- `GET /api/attachments/{attachmentId}` and `/download` must use the same authorization path.

## Tests And Evidence
- Automated
  - adapter tests for filesystem write, read, and delete behavior
  - service tests for size-limit validation and chat-authorization checks
  - HTTP integration tests for multipart upload, metadata reads, download authorization, room-removal access loss, and room-delete cleanup
- Manual
  - UI proof for file upload, image upload, paste upload, and download
  - note showing a removed or banned user immediately loses attachment access

## Exit Criteria
- Attachments behave like message-bound chat content and obey request-time authorization.
- Room deletion removes blobs as well as metadata.
- The milestone ends with a green `./gradlew test` and a dated evidence note in `docs/evidence/`.
