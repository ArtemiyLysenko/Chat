import { bindLogoutButton, clearMessage, jsonRequest, writeMessage } from "./common.js";
import { createChatSurface } from "./chat-view.js";

bindLogoutButton(document.querySelector("[data-logout-button]"));

const titleElement = document.querySelector("[data-dialog-title]");
const subtitleElement = document.querySelector("[data-dialog-subtitle]");
const messageElement = document.querySelector("[data-dialog-message]");
const participantElement = document.querySelector("[data-dialog-participant]");
const identityElement = document.querySelector("[data-dialog-identity]");
const placeholderElement = document.querySelector("[data-dialog-placeholder]");
const refreshButton = document.querySelector("[data-direct-refresh-button]");
const refreshChatButton = document.querySelector("[data-direct-refresh-chat-button]");
const friendsListElement = document.querySelector("[data-direct-friends-list]");

const formatDate = (value) =>
  new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));

const mutedBlock = (text) => {
  const element = document.createElement("div");
  element.className = "muted-block";
  element.textContent = text;
  return element;
};

const detailLine = (label, value) => {
  const wrapper = document.createElement("div");
  const heading = document.createElement("strong");
  heading.textContent = label;
  const detail = document.createElement("div");
  detail.className = "subtle";
  detail.textContent = value;
  wrapper.append(heading, detail);
  return wrapper;
};

const participantUserId = () => {
  const segments = window.location.pathname.split("/").filter(Boolean);
  return segments.at(-1) ?? "";
};

const state = {
  contacts: null,
  dialog: null,
};

const directChatSurface = createChatSurface({
  statusElement: messageElement,
  emptyElement: document.querySelector("[data-chat-empty]"),
  timelineElement: document.querySelector("[data-chat-timeline]"),
  loadOlderButton: document.querySelector("[data-chat-load-older-button]"),
  composerForm: document.querySelector("[data-chat-composer-form]"),
  bodyInput: document.querySelector("[data-chat-body-input]"),
  submitButton: document.querySelector("[data-chat-submit-button]"),
  replyBanner: document.querySelector("[data-chat-reply-target]"),
  editBanner: document.querySelector("[data-chat-edit-target]"),
  cancelReplyButton: document.querySelector("[data-chat-cancel-reply-button]"),
  cancelEditButton: document.querySelector("[data-chat-cancel-edit-button]"),
  formatDate,
  onConversationChanged: async () => {
    await refreshContacts();
  },
});

const renderFriendsList = () => {
  friendsListElement.replaceChildren();
  const friends = state.contacts?.friends ?? [];
  if (friends.length === 0) {
    friendsListElement.append(mutedBlock("No accepted friends are available for direct dialogs."));
    return;
  }

  for (const friend of friends) {
    const article = document.createElement("article");
    article.className = "room-card";
    if (friend.user.id === participantUserId()) {
      article.classList.add("room-card--active");
    }

    const top = document.createElement("div");
    top.className = "split";

    const info = document.createElement("div");
    const title = document.createElement("strong");
    title.textContent = friend.user.deleted ? friend.user.displayName : `${friend.user.displayName} (@${friend.user.username})`;
    const meta = document.createElement("div");
    meta.className = "subtle";
    meta.textContent = friend.directDialogId
      ? `Dialog ready since ${formatDate(friend.friendsSince)}`
      : `Open to create or reuse the stable dialog identity`;
    info.append(title, meta);
    if (friend.user.deleted) {
      info.append(detailLine("Identity note", "Tombstoned account"));
    }

    const status = document.createElement("div");
    status.className = "stack inline-stack";
    if (friend.unreadCount > 0) {
      const badge = document.createElement("span");
      badge.className = "pill badge";
      badge.textContent = `${friend.unreadCount} unread`;
      status.append(badge);
    }
    if (friend.user.id === participantUserId()) {
      const active = document.createElement("span");
      active.className = "pill neutral";
      active.textContent = "Open";
      status.append(active);
    }

    top.append(info, status);

    const actions = document.createElement("div");
    actions.className = "actions";
    const openButton = document.createElement("button");
    openButton.type = "button";
    openButton.className = "secondary";
    openButton.textContent = friend.user.id === participantUserId() ? "Viewing" : "Open dialog";
    openButton.disabled = friend.user.id === participantUserId();
    openButton.addEventListener("click", () => {
      window.location.assign(`/app/direct-dialogs/${friend.user.id}`);
    });
    actions.append(openButton);

    article.append(top, actions);
    friendsListElement.append(article);
  }
};

const renderDialogDetails = (dialog) => {
  titleElement.textContent = `Direct dialog with ${dialog.participant.displayName}`;
  subtitleElement.textContent = dialog.created
    ? "A new stable direct-dialog identity was created for this friend pair."
    : "An existing stable direct-dialog identity was reused for this friend pair.";

  participantElement.replaceChildren(
    detailLine("Display name", dialog.participant.displayName),
    detailLine("Username", dialog.participant.deleted ? "Tombstoned account" : `@${dialog.participant.username}`),
    detailLine("User id", dialog.participant.id),
  );

  identityElement.replaceChildren(
    detailLine("Dialog id", dialog.dialogId),
    detailLine("Chat reference", `DIRECT:${dialog.dialogId}`),
    detailLine("Established", formatDate(dialog.createdAt)),
  );

  placeholderElement.textContent = "This screen still reads and mutates direct messages over HTTP. The backend /ws channel is live, and browser-side live updates land in Milestone 4.4.";
};

const refreshContacts = async () => {
  state.contacts = await jsonRequest("/api/contacts");
  renderFriendsList();
};

const loadPage = async () => {
  const userId = participantUserId();
  if (!userId) {
    writeMessage(messageElement, "error", "Participant id is missing from the route.");
    titleElement.textContent = "Direct dialog unavailable";
    return;
  }

  clearMessage(messageElement);
  await refreshContacts();

  try {
    const dialog = await jsonRequest(`/api/direct-dialogs/${userId}`, { method: "POST" });
    state.dialog = dialog;
    renderDialogDetails(dialog);
    await directChatSurface.open({
      chatType: "DIRECT",
      chatId: dialog.dialogId,
      currentUserId: state.contacts.viewerUserId,
      viewerRole: null,
      placeholder: `Write to ${dialog.participant.displayName}`,
    });
  } catch (error) {
    state.dialog = null;
    directChatSurface.clear();
    titleElement.textContent = "Direct dialog unavailable";
    subtitleElement.textContent = "This route still starts from the accepted-friends navigation state.";
    participantElement.replaceChildren(mutedBlock("No participant details are available."));
    identityElement.replaceChildren(mutedBlock("No stable dialog identity is currently available from this route."));
    placeholderElement.textContent = "Existing direct-dialog history remains preserved for participants, but this route only reopens dialogs through the current accepted-friends flow.";
    writeMessage(messageElement, "error", error.message);
  }
};

refreshButton.addEventListener("click", async () => {
  refreshButton.disabled = true;
  try {
    await loadPage();
  } finally {
    refreshButton.disabled = false;
  }
});

refreshChatButton.addEventListener("click", async () => {
  refreshChatButton.disabled = true;
  try {
    await directChatSurface.refresh();
  } catch (error) {
    writeMessage(messageElement, "error", error.message);
  } finally {
    refreshChatButton.disabled = false;
  }
});

loadPage().catch((error) => {
  writeMessage(messageElement, "error", error.message);
});
