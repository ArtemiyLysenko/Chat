# Milestone 5.2 Evidence

- Date: 2026-04-21
- Slice: Milestone 5 closeout for attachment UI, upload and paste flows, browser-side access loss, and room-delete cleanup

## Scope Validated

- Room timeline attachment rendering, upload button flow, and download actions
- Direct-dialog attachment rendering and upload flow
- Paste-image upload flow from the room composer
- Immediate attachment access loss after room membership removal on an already-open stale browser page
- Room deletion removing both attachment metadata and mounted filesystem blobs while preserving unrelated direct-dialog attachments

## Code And Doc Surface Checked

- Room and direct-dialog static pages now expose attachment controls and notes
- Shared chat surface now handles multipart uploads, paste-image events, attachment rendering, binary downloads, and browser-side access refresh on `403` or `404`
- Room deletion now removes room attachment blobs after transaction commit
- Attachment HTTP integration coverage now includes member removal, ban access loss, and room-delete cleanup

## Automated Verification

- Targeted attachment HTTP suite after the final review fix:

```bash
./gradlew :apps:api:test --tests '*AttachmentsHttpIntegrationTests' --no-daemon
```

- Outcome: `BUILD SUCCESSFUL`

- Full project verification after the final review fix:

```bash
./gradlew test --no-daemon
```

- Outcome: `BUILD SUCCESSFUL`

## Browser And Manual Verification

- Live app boot:

```bash
APP_PORT=18080 docker compose up -d --build app
```

- Created isolated owner and member users, a private room, and a direct dialog against the real HTTP API with CSRF cookies and persistent session cookies.
- Owner room upload-button flow succeeded on the browser page and emitted `POST /api/chats/room/c4a5fee5-b90b-436e-a6d7-8c47ba357783/attachments [201]` in the DevTools network log.
- Direct-dialog upload succeeded through the same hidden file-input change handler. DevTools could not attach directly to the hidden input on that page, so the browser proof set the file on the real input element via DOM automation and observed `POST /api/chats/direct/fc067213-2499-46f6-89f9-53726f40fa53/attachments [201]`.
- Paste-image proof dispatched a browser `paste` event with a PNG payload on the room composer and rendered `pasted-image.png` in the room timeline with the comment text preserved.
- Download proof from the room timeline emitted `GET /api/attachments/90d4cd00-2f20-4c98-a517-99674667d1bb/download [200]`.
- Access-loss proof on the stale removed-member page emitted:
  - `GET /api/attachments/90d4cd00-2f20-4c98-a517-99674667d1bb/download [403]`
  - followed by `GET /api/rooms/7bc42e1c-10f0-4434-ab55-fc9133a57d0c [404]`
  - and the page collapsed back to `/app` with the room removed from joined rooms.

## Cleanup Verification

- Before deleting the moderated room, host-mounted blob files were:

```text
storage/uploads/90d4cd00-2f20-4c98-a517-99674667d1bb
storage/uploads/9beb8331-1312-4d98-9482-cd3e6e432cf9
storage/uploads/b10643c0-4dca-4c77-99b6-b9839b960f6a
```

- Deleted the room through the real HTTP API and then checked filesystem plus metadata:

```text
FILES AFTER DELETE
storage/uploads/b10643c0-4dca-4c77-99b6-b9839b960f6a

ROOM ATTACHMENT STATUS
404

DIRECT ATTACHMENT STATUS
200
```

- Result: both room-owned blobs were removed, room attachment metadata was gone, and the unrelated direct-dialog attachment remained readable.

## Saved Artifacts

- Room attachment UI: [2026-04-21-milestone-5-2-room-attachment-ui.png](2026-04-21-milestone-5-2-room-attachment-ui.png)
- Direct-dialog attachment UI: [2026-04-21-milestone-5-2-direct-attachment-ui.png](2026-04-21-milestone-5-2-direct-attachment-ui.png)
- Removed-member access loss: [2026-04-21-milestone-5-2-room-access-loss.png](2026-04-21-milestone-5-2-room-access-loss.png)

## Result

- Milestone 5.2 exit criteria are met.
- Milestone 5 is now completed with green automated verification and browser-backed attachment evidence.
