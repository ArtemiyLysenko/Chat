# Requirements Summary

Source: `2026_04_18_AI_herders_jam_-_requirements_v3 1.docx`
This file is a working summary for planning. The `.docx` remains the authoritative acceptance source.

## Accounts And Authentication
- Self-registration uses unique email, unique username, and password.
- Username is immutable after registration.
- Email verification is not required.
- Sign in uses email and password.
- Sign out affects only the current browser session. Other active sessions stay valid.
- Login persists across browser close and reopen.
- Password reset and password change are required.
- Passwords must be stored securely in hashed form.
- Users can view active sessions with browser and IP details and log out selected sessions.
- Account deletion removes the user account, deletes rooms they own, permanently deletes those rooms' messages and attachments, and removes the user from other room memberships.

## Presence And Multi-Tab Behavior
- Presence states are `online`, `AFK`, and `offline`.
- A user is `AFK` only when all open tabs have been inactive for more than 1 minute.
- A user is `online` if at least one open tab is active.
- A user becomes `offline` only when all tabs are closed or offloaded by the browser.
- Presence updates should propagate with low latency and target under 2 seconds.

## Contacts And Personal Messaging
- Each user has a contacts or friends list.
- Friend requests can be sent by username or from room member lists and may include optional text.
- Friendship requires recipient confirmation.
- Users can remove friends.
- Users can ban another user.
- A user-to-user ban blocks new contact, terminates the friendship relation, and leaves existing direct-message history visible but frozen.
- Personal messaging is allowed only when both users are friends and neither side has banned the other.

## Rooms And Moderation
- Any registered user can create a room.
- Rooms have a unique name, description, visibility, owner, admins, members, and banned users.
- Public rooms appear in a searchable catalog and can be freely joined by authenticated users unless banned.
- Private rooms are hidden from the catalog and can be joined only by invitation.
- Users can leave rooms freely except for the owner, who cannot leave their own room and must delete it instead.
- Admin removal from a room is treated as a ban.
- Users removed or banned from a room lose UI access to room history and to all files and images in that room.
- Admins can delete messages, remove members, ban and unban users, inspect who placed bans, and manage other admins except the owner.
- Owners can perform all admin actions, remove any admin or member, and delete the room.

## Messaging And History
- Room chats and personal dialogs share the same user-facing messaging capabilities.
- Messages support plain text, multiline text, emoji, attachments, and replies.
- Maximum message text size is 3 KB and text must support UTF-8.
- Users can edit their own messages and the UI must mark edited messages.
- Messages can be deleted by authors and by room admins in room chats.
- Messages are stored persistently, shown in chronological order, and old history loads through infinite scroll.
- Messages sent while recipients are offline must persist and appear when they next connect.
- Unread indicators appear next to rooms and direct dialogs and clear when the user opens that chat.

## Attachments And Access Control
- Users can send images and arbitrary file types.
- Attachments can be added with an upload button or by copy and paste.
- Original filenames must be preserved.
- Users may add an optional comment to an attachment.
- Files and images can be downloaded only by current room members or authorized direct-chat participants.
- If a user loses access to a room, they also lose access to that room's files and images.
- Uploaded files persist unless the room itself is deleted, even if the original uploader later loses access.
- Attachments are stored on the local filesystem.
- Maximum file size is 20 MB.
- Maximum image size is 3 MB.

## Non-Functional Constraints
- Up to 300 simultaneous connected users
- Up to 1000 participants in a room
- Message delivery target under 3 seconds
- Large history remains usable at 10,000+ messages
- Project must run from the repository root via `docker compose up`
- Membership, ban lists, file access rights, message history, and admin or owner permissions must remain consistent

## UX Direction
- Classic web chat layout
- Top menu with sign-in, register, rooms, contacts, sessions, profile, and sign-out navigation
- Rooms and contacts live in the right sidebar
- Side lists for rooms and contacts
- Central message panel
- Members and room context panel
- Multiline message input with emoji, attach, and reply affordances
- Modal-based admin actions

## Advanced Requirements
- Jabber/XMPP client support is mandatory, not optional.
- The system must support federation between servers, which implies a more complex multi-server `docker compose` setup than the single-server baseline.
- The implementation must use a Jabber/XMPP library that fits the governed Java and Spring Boot stack.
- The server UI must include an admin connection dashboard for Jabber/XMPP connectivity.
- The server UI must include federation traffic information and statistics.
- The strongest acceptance target is a two-server federation load test with at least 50 connected clients on server A, at least 50 connected clients on server B, and bidirectional messaging between A and B.
