import { bindAsyncForm, bindLogoutButton, clearMessage, createCoalescedTask, jsonRequest, writeMessage } from "./common.js";
import { createChatSurface } from "./chat-view.js";
import { createLiveUpdatesClient } from "./live-updates.js";

bindLogoutButton(document.querySelector("[data-logout-button]"));

const refreshRoomsButton = document.querySelector("[data-refresh-rooms-button]");
const joinedRoomsElement = document.querySelector("[data-joined-rooms]");
const catalogRoomsElement = document.querySelector("[data-catalog-rooms]");
const roomMessage = document.querySelector("[data-room-message]");
const createRoomForm = document.querySelector("[data-create-room-form]");
const createRoomMessage = document.querySelector("[data-create-room-message]");
const openRoomForm = document.querySelector("[data-open-room-form]");
const openRoomMessage = document.querySelector("[data-open-room-message]");
const roomEmpty = document.querySelector("[data-room-empty]");
const roomContent = document.querySelector("[data-room-content]");
const roomTitle = document.querySelector("[data-room-title]");
const roomMeta = document.querySelector("[data-room-meta]");
const roomActions = document.querySelector("[data-room-actions]");
const roomPreview = document.querySelector("[data-room-preview]");
const roomMembersCard = document.querySelector("[data-room-members-card]");
const roomMembers = document.querySelector("[data-room-members]");
const roomModerationCard = document.querySelector("[data-room-moderation-card]");
const roomModerationMessage = document.querySelector("[data-room-moderation-message]");
const roomInviteControls = document.querySelector("[data-room-invite-controls]");
const roomBanControls = document.querySelector("[data-room-ban-controls]");
const roomBanList = document.querySelector("[data-room-ban-list]");
const roomMessagePanel = document.querySelector("[data-room-message-panel]");
const roomRefreshChatButton = document.querySelector("[data-room-refresh-chat-button]");
const roomChatStatus = document.querySelector("[data-room-chat-status]");
const changePasswordForm = document.querySelector("[data-password-change-form]");
const changePasswordMessage = document.querySelector("[data-password-change-message]");
const deleteAccountForm = document.querySelector("[data-account-delete-form]");
const deleteAccountMessage = document.querySelector("[data-account-delete-message]");

const state = {
  joinedRooms: [],
  catalogRooms: [],
  selectedRoomId: new URLSearchParams(window.location.search).get("room") ?? "",
  selectedRoom: null,
  bans: [],
};

const formatDate = (value) =>
  new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));

const roomChatSurface = createChatSurface({
  statusElement: roomChatStatus,
  emptyElement: roomMessagePanel.querySelector("[data-chat-empty]"),
  timelineElement: roomMessagePanel.querySelector("[data-chat-timeline]"),
  loadOlderButton: roomMessagePanel.querySelector("[data-chat-load-older-button]"),
  composerForm: roomMessagePanel.querySelector("[data-chat-composer-form]"),
  bodyInput: roomMessagePanel.querySelector("[data-chat-body-input]"),
  submitButton: roomMessagePanel.querySelector("[data-chat-submit-button]"),
  replyBanner: roomMessagePanel.querySelector("[data-chat-reply-target]"),
  editBanner: roomMessagePanel.querySelector("[data-chat-edit-target]"),
  cancelReplyButton: roomMessagePanel.querySelector("[data-chat-cancel-reply-button]"),
  cancelEditButton: roomMessagePanel.querySelector("[data-chat-cancel-edit-button]"),
  formatDate,
  onConversationChanged: async () => {
    await refreshRooms();
  },
});

const roomUrl = (roomId) => `/app?room=${encodeURIComponent(roomId)}`;

const setSelectedRoom = async (roomId, { silent = false } = {}) => {
  const nextRoomId = roomId ?? "";
  if (nextRoomId !== state.selectedRoomId) {
    clearMessage(roomModerationMessage);
  }
  state.selectedRoomId = nextRoomId;
  const nextUrl = state.selectedRoomId ? roomUrl(state.selectedRoomId) : "/app";
  window.history.replaceState(null, "", nextUrl);
  if (!silent) {
    clearMessage(openRoomMessage);
  }
  await loadSelectedRoom();
};

const summaryLine = (text) => {
  const element = document.createElement("div");
  element.className = "subtle";
  element.textContent = text;
  return element;
};

const mutedBlock = (text) => {
  const element = document.createElement("div");
  element.className = "muted-block";
  element.textContent = text;
  return element;
};

const rolePill = (text, extraClass = "") => {
  const element = document.createElement("span");
  element.className = `pill${extraClass ? ` ${extraClass}` : ""}`;
  element.textContent = text;
  return element;
};

const actionButton = (label, kind = "primary") => {
  const button = document.createElement("button");
  button.type = "button";
  button.className = kind === "primary" ? "" : kind;
  button.textContent = label;
  return button;
};

const roomCard = (room) => {
  const article = document.createElement("article");
  article.className = "room-card";

  const top = document.createElement("div");
  top.className = "split";

  const titleWrap = document.createElement("div");
  const title = document.createElement("strong");
  title.textContent = room.name;
  titleWrap.append(title, summaryLine(`${room.owner.displayName} · ${room.visibility.toLowerCase()} room`));
  if (room.description) {
    titleWrap.append(summaryLine(room.description));
  }

  const status = document.createElement("div");
  status.className = "stack inline-stack";
  if (room.viewerRole) {
    status.append(rolePill(room.viewerRole));
  }
  status.append(rolePill(`${room.memberCount} members`, "neutral"));
  if (room.unreadCount > 0) {
    status.append(rolePill(`${room.unreadCount} unread`, "badge"));
  }

  top.append(titleWrap, status);

  const actions = document.createElement("div");
  actions.className = "actions";

  const openButton = actionButton("Open", room.viewerRole ? "primary" : "secondary");
  openButton.addEventListener("click", async () => {
    await setSelectedRoom(room.id);
  });
  actions.append(openButton);

  if (!room.viewerRole && room.visibility === "PUBLIC") {
    const joinButton = actionButton("Join");
    joinButton.addEventListener("click", async () => {
      try {
        await jsonRequest(`/api/rooms/${room.id}/join`, { method: "POST" });
        writeMessage(roomMessage, "success", `Joined ${room.name}.`);
        await refreshRooms();
        await setSelectedRoom(room.id, { silent: true });
      } catch (error) {
        writeMessage(roomMessage, "error", error.message);
      }
    });
    actions.append(joinButton);
  }

  article.append(top, actions);
  return article;
};

const renderJoinedRooms = () => {
  joinedRoomsElement.replaceChildren();
  if (state.joinedRooms.length === 0) {
    joinedRoomsElement.append(mutedBlock("No joined rooms yet."));
    return;
  }
  for (const room of state.joinedRooms) {
    joinedRoomsElement.append(roomCard(room));
  }
};

const renderCatalogRooms = () => {
  catalogRoomsElement.replaceChildren();
  if (state.catalogRooms.length === 0) {
    catalogRoomsElement.append(mutedBlock("No public rooms are currently visible."));
    return;
  }
  for (const room of state.catalogRooms) {
    catalogRoomsElement.append(roomCard(room));
  }
};

const renderMembers = (details) => {
  roomMembers.replaceChildren();
  if (details.members.length === 0) {
    roomMembers.append(mutedBlock("No members to display."));
    return;
  }

  for (const member of details.members) {
    const item = document.createElement("article");
    item.className = "member-item";

    const info = document.createElement("div");
    const name = document.createElement("strong");
    name.textContent = `${member.user.displayName} (@${member.user.username})`;
    info.append(name, summaryLine(`${member.role} · joined ${formatDate(member.joinedAt)}`));

    const actions = document.createElement("div");
    actions.className = "actions";

    if (member.canGrantAdmin) {
      const grantButton = actionButton("Grant admin");
      grantButton.addEventListener("click", async () => {
        try {
          await jsonRequest(`/api/rooms/${details.id}/admins/${member.user.id}`, { method: "PUT" });
          writeMessage(roomModerationMessage, "success", `Granted admin to ${member.user.displayName}.`);
          await refreshRooms();
          await loadSelectedRoom();
        } catch (error) {
          writeMessage(roomModerationMessage, "error", error.message);
        }
      });
      actions.append(grantButton);
    }

    if (member.canRevokeAdmin) {
      const revokeButton = actionButton("Revoke admin", "secondary");
      revokeButton.addEventListener("click", async () => {
        try {
          await jsonRequest(`/api/rooms/${details.id}/admins/${member.user.id}`, { method: "DELETE" });
          writeMessage(roomModerationMessage, "success", `Revoked admin from ${member.user.displayName}.`);
          await refreshRooms();
          await loadSelectedRoom();
        } catch (error) {
          writeMessage(roomModerationMessage, "error", error.message);
        }
      });
      actions.append(revokeButton);
    }

    if (member.canRemove) {
      const removeLabel = member.role === "ADMIN" ? "Remove + ban" : "Remove";
      const removeButton = actionButton(removeLabel, member.role === "ADMIN" ? "danger" : "secondary");
      removeButton.addEventListener("click", async () => {
        try {
          await jsonRequest(`/api/rooms/${details.id}/members/${member.user.id}`, { method: "DELETE" });
          writeMessage(
            roomModerationMessage,
            "success",
            member.role === "ADMIN"
              ? `${member.user.displayName} was removed and banned.`
              : `${member.user.displayName} was removed.`
          );
          await refreshRooms();
          await loadSelectedRoom();
        } catch (error) {
          writeMessage(roomModerationMessage, "error", error.message);
        }
      });
      actions.append(removeButton);
    }

    item.append(info);
    if (actions.childElementCount > 0) {
      item.append(actions);
    }
    roomMembers.append(item);
  }
};

const renderInviteControls = (details) => {
  roomInviteControls.replaceChildren();
  if (!details.canInvite) {
    return;
  }

  const info = document.createElement("div");
  info.className = "muted-block";
  info.textContent = `Share this room link with invitees after creating an invite: ${window.location.origin}${roomUrl(details.id)}`;
  roomInviteControls.append(info);

  const form = document.createElement("form");
  form.className = "form-grid";
  const label = document.createElement("label");
  label.textContent = "Invite user id";
  const input = document.createElement("input");
  input.name = "userId";
  input.required = true;
  label.append(input);
  form.append(label);

  const actions = document.createElement("div");
  actions.className = "actions";
  const submit = actionButton("Invite");
  submit.type = "submit";
  actions.append(submit);
  form.append(actions);

  const message = document.createElement("div");
  message.className = "message";

  bindAsyncForm(form, message, async (formData) => {
    await jsonRequest(`/api/rooms/${details.id}/invites`, {
      method: "POST",
      body: { userId: formData.get("userId") },
    });
    form.reset();
    writeMessage(message, "success", "Private-room invite recorded.");
  });

  roomInviteControls.append(form, message);
};

const renderBanControls = (details) => {
  roomBanControls.replaceChildren();
  roomBanList.replaceChildren();

  if (details.canManageBans) {
    const form = document.createElement("form");
    form.className = "form-grid";

    const userLabel = document.createElement("label");
    userLabel.textContent = "Ban user id";
    const userInput = document.createElement("input");
    userInput.name = "userId";
    userInput.required = true;
    userLabel.append(userInput);

    const reasonLabel = document.createElement("label");
    reasonLabel.textContent = "Reason";
    const reasonInput = document.createElement("textarea");
    reasonInput.name = "reason";
    reasonInput.rows = 3;
    reasonLabel.append(reasonInput);

    form.append(userLabel, reasonLabel);

    const actions = document.createElement("div");
    actions.className = "actions";
    const submit = actionButton("Ban user", "danger");
    submit.type = "submit";
    actions.append(submit);
    form.append(actions);

    const message = document.createElement("div");
    message.className = "message";

    bindAsyncForm(form, message, async (formData) => {
      await jsonRequest(`/api/rooms/${details.id}/bans/${formData.get("userId")}`, {
        method: "PUT",
        body: { reason: formData.get("reason") || null },
      });
      form.reset();
      writeMessage(message, "success", "Ban updated.");
      await loadSelectedRoom();
      await refreshRooms();
    });

    roomBanControls.append(form, message);
  }

  if (!details.canInspectBans) {
    return;
  }

  const title = document.createElement("h4");
  title.textContent = "Current bans";
  roomBanList.append(title);

  if (state.bans.length === 0) {
    roomBanList.append(mutedBlock("No active bans."));
    return;
  }

  for (const ban of state.bans) {
    const item = document.createElement("article");
    item.className = "ban-item";

    const info = document.createElement("div");
    const name = document.createElement("strong");
    name.textContent = `${ban.user.displayName} (@${ban.user.username})`;
    info.append(name, summaryLine(`Banned by ${ban.actor.displayName} on ${formatDate(ban.createdAt)}`));
    if (ban.reason) {
      info.append(summaryLine(`Reason: ${ban.reason}`));
    }

    item.append(info);

    if (details.canManageBans) {
      const actions = document.createElement("div");
      actions.className = "actions";
      const unbanButton = actionButton("Unban", "secondary");
      unbanButton.addEventListener("click", async () => {
        try {
          await jsonRequest(`/api/rooms/${details.id}/bans/${ban.user.id}`, { method: "DELETE" });
          writeMessage(roomModerationMessage, "success", `Unbanned ${ban.user.displayName}.`);
          await loadSelectedRoom();
          await refreshRooms();
        } catch (error) {
          writeMessage(roomModerationMessage, "error", error.message);
        }
      });
      actions.append(unbanButton);
      item.append(actions);
    }

    roomBanList.append(item);
  }
};

const renderRoomActions = (details) => {
  roomActions.replaceChildren();

  if (details.canJoin) {
    const joinButton = actionButton("Join room");
    joinButton.addEventListener("click", async () => {
      try {
        await jsonRequest(`/api/rooms/${details.id}/join`, { method: "POST" });
        writeMessage(roomMessage, "success", `Joined ${details.name}.`);
        await refreshRooms();
        await loadSelectedRoom();
      } catch (error) {
        writeMessage(roomMessage, "error", error.message);
      }
    });
    roomActions.append(joinButton);
  }

  if (details.canLeave) {
    const leaveButton = actionButton("Leave room", "secondary");
    leaveButton.addEventListener("click", async () => {
      try {
        await jsonRequest(`/api/rooms/${details.id}/leave`, { method: "POST" });
        writeMessage(roomMessage, "success", `Left ${details.name}.`);
        await refreshRooms();
        await setSelectedRoom("", { silent: true });
      } catch (error) {
        writeMessage(roomMessage, "error", error.message);
      }
    });
    roomActions.append(leaveButton);
  }

  if (details.canDelete) {
    const deleteButton = actionButton("Delete room", "danger");
    deleteButton.addEventListener("click", async () => {
      const confirmed = window.confirm(`Delete ${details.name}? This permanently removes room-owned state.`);
      if (!confirmed) {
        return;
      }
      try {
        await jsonRequest(`/api/rooms/${details.id}`, { method: "DELETE" });
        writeMessage(roomMessage, "success", `${details.name} was deleted.`);
        await refreshRooms();
        await setSelectedRoom("", { silent: true });
      } catch (error) {
        writeMessage(roomMessage, "error", error.message);
      }
    });
    roomActions.append(deleteButton);
  }
};

const renderSelectedRoom = () => {
  if (!state.selectedRoom) {
    roomEmpty.hidden = false;
    roomContent.hidden = true;
    roomTitle.textContent = "";
    roomMeta.textContent = "";
    roomPreview.hidden = true;
    clearMessage(roomModerationMessage);
    roomMembers.replaceChildren();
    roomInviteControls.replaceChildren();
    roomBanControls.replaceChildren();
    roomBanList.replaceChildren();
    roomMessagePanel.hidden = true;
    return;
  }

  const details = state.selectedRoom;
  roomEmpty.hidden = true;
  roomContent.hidden = false;
  roomTitle.textContent = details.name;
  roomMeta.textContent =
    details.accessLevel === "INVITED_PREVIEW"
      ? `Owned by ${details.owner.displayName} (@${details.owner.username})`
      : `${details.visibility.toLowerCase()} room · ${details.memberCount} members · owned by ${details.owner.displayName} (@${details.owner.username})`;

  renderRoomActions(details);

  if (details.accessLevel === "INVITED_PREVIEW") {
    roomPreview.hidden = false;
    roomPreview.textContent = "Private-room invite preview. Only the room name and owner are visible until you join.";
    roomMembersCard.hidden = true;
    roomModerationCard.hidden = true;
    roomMessagePanel.hidden = true;
    roomInviteControls.replaceChildren();
    roomBanControls.replaceChildren();
    roomBanList.replaceChildren();
    return;
  }

  roomPreview.hidden = true;
  roomMembersCard.hidden = false;
  roomModerationCard.hidden = false;
  roomMessagePanel.hidden = details.viewerRole == null;
  renderMembers(details);
  renderInviteControls(details);
  renderBanControls(details);
};

const refreshRooms = async () => {
  const [joinedRooms, catalogRooms] = await Promise.all([
    jsonRequest("/api/rooms?scope=joined"),
    jsonRequest("/api/rooms?scope=catalog"),
  ]);
  state.joinedRooms = joinedRooms;
  state.catalogRooms = catalogRooms;
  renderJoinedRooms();
  renderCatalogRooms();
};

const loadSelectedRoom = async () => {
  if (!state.selectedRoomId) {
    state.selectedRoom = null;
    state.bans = [];
    renderSelectedRoom();
    roomChatSurface.clear();
    return;
  }

  try {
    const details = await jsonRequest(`/api/rooms/${state.selectedRoomId}`);
    state.selectedRoom = details;
    state.bans = details.canInspectBans ? await jsonRequest(`/api/rooms/${state.selectedRoomId}/bans`) : [];
    renderSelectedRoom();
    if (details.accessLevel === "FULL" && details.viewerRole) {
      await roomChatSurface.open({
        chatType: "ROOM",
        chatId: details.id,
        currentUserId: details.viewerUserId,
        viewerRole: details.viewerRole,
        placeholder: `Write to ${details.name}`,
      });
    } else {
      roomChatSurface.clear();
    }
  } catch (error) {
    state.selectedRoom = null;
    state.bans = [];
    renderSelectedRoom();
    roomChatSurface.clear();
    writeMessage(roomMessage, "error", error.message);
    if (error.message.includes("not found")) {
      state.selectedRoomId = "";
      window.history.replaceState(null, "", "/app");
    }
    throw error;
  }
};

const refreshRoomListsLive = createCoalescedTask(async () => {
  await refreshRooms();
});

const refreshSelectedRoomLive = createCoalescedTask(async () => {
  await refreshRooms();
  await loadSelectedRoom();
});

const refreshOpenRoomChatLive = createCoalescedTask(async () => {
  await roomChatSurface.refresh();
});

const shouldRefreshOpenRoomChat = (event) =>
  event?.chat?.type === "ROOM"
  && state.selectedRoom?.accessLevel === "FULL"
  && state.selectedRoom?.viewerRole != null
  && state.selectedRoom?.id === event.chat.id;

const handleLiveRoomEvent = async (event) => {
  switch (event?.type) {
    case "message.created":
    case "message.updated":
    case "message.deleted":
      if (shouldRefreshOpenRoomChat(event)) {
        await refreshOpenRoomChatLive();
      }
      break;
    case "unread.updated":
      if (event?.chat?.type === "ROOM") {
        await refreshRoomListsLive();
      }
      break;
    default:
      break;
  }
};

createLiveUpdatesClient({
  onEvent: handleLiveRoomEvent,
  onReconnect: refreshSelectedRoomLive,
});

roomRefreshChatButton.addEventListener("click", async () => {
  roomRefreshChatButton.disabled = true;
  try {
    await roomChatSurface.refresh();
  } catch (error) {
    writeMessage(roomChatStatus, "error", error.message);
  } finally {
    roomRefreshChatButton.disabled = false;
  }
});

refreshRoomsButton.addEventListener("click", async () => {
  refreshRoomsButton.disabled = true;
  try {
    await refreshRooms();
    await loadSelectedRoom();
    writeMessage(roomMessage, "success", "Room lists refreshed.");
  } catch (error) {
    writeMessage(roomMessage, "error", error.message);
  } finally {
    refreshRoomsButton.disabled = false;
  }
});

bindAsyncForm(createRoomForm, createRoomMessage, async (formData) => {
  const createdRoom = await jsonRequest("/api/rooms", {
    method: "POST",
    body: {
      name: formData.get("name"),
      description: formData.get("description"),
      visibility: formData.get("visibility"),
    },
  });

  createRoomForm.reset();
  writeMessage(createRoomMessage, "success", `Created ${createdRoom.name}.`);
  await refreshRooms();
  await setSelectedRoom(createdRoom.id, { silent: true });
});

bindAsyncForm(openRoomForm, openRoomMessage, async (formData) => {
  await setSelectedRoom(String(formData.get("roomId") ?? "").trim());
  openRoomForm.reset();
  writeMessage(openRoomMessage, "success", "Room loaded.");
});

bindAsyncForm(changePasswordForm, changePasswordMessage, async (formData) => {
  await jsonRequest("/api/auth/password/change", {
    method: "POST",
    body: {
      currentPassword: formData.get("currentPassword"),
      newPassword: formData.get("newPassword"),
    },
  });

  changePasswordForm.reset();
  writeMessage(changePasswordMessage, "success", "Password changed. Other sessions were revoked.");
});

bindAsyncForm(deleteAccountForm, deleteAccountMessage, async (formData) => {
  const confirmed = window.confirm(
    "Delete this account now? Owned rooms will be deleted and other room memberships will be cleaned up."
  );
  if (!confirmed) {
    return;
  }

  await jsonRequest("/api/account", {
    method: "DELETE",
    body: {
      currentPassword: formData.get("currentPassword"),
    },
  });

  writeMessage(deleteAccountMessage, "success", "Account deleted. Redirecting...");
  window.setTimeout(() => window.location.assign("/"), 500);
});

const boot = async () => {
  try {
    await refreshRooms();
    await loadSelectedRoom();
  } catch (error) {
    writeMessage(roomMessage, "error", error.message);
  }
};

boot();
