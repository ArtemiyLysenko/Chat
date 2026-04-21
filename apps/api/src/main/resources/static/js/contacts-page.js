import { bindAsyncForm, bindLogoutButton, clearMessage, jsonRequest, writeMessage } from "./common.js";

bindLogoutButton(document.querySelector("[data-logout-button]"));

const refreshContactsButton = document.querySelector("[data-refresh-contacts-button]");
const createRequestForm = document.querySelector("[data-create-request-form]");
const createRequestMessage = document.querySelector("[data-create-request-message]");
const contactsMessage = document.querySelector("[data-contacts-message]");
const friendsList = document.querySelector("[data-friends-list]");
const inboundRequestsList = document.querySelector("[data-inbound-requests-list]");
const outboundRequestsList = document.querySelector("[data-outbound-requests-list]");
const blockedUsersList = document.querySelector("[data-blocked-users-list]");

const state = {
  contacts: {
    friends: [],
    inboundPendingRequests: [],
    outboundPendingRequests: [],
    blockedUsers: [],
  },
};

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

const summaryLine = (text) => {
  const element = document.createElement("div");
  element.className = "subtle";
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

const userLabel = (user) => `${user.displayName} (@${user.username})`;

const performContactsAction = async (request, successMessage) => {
  try {
    await request();
    writeMessage(contactsMessage, "success", successMessage);
    await refreshContacts({ preserveMessage: true });
  } catch (error) {
    writeMessage(contactsMessage, "error", error.message);
  }
};

const contactCard = (title, details, actions = []) => {
  const article = document.createElement("article");
  article.className = "contact-card";

  const info = document.createElement("div");
  const name = document.createElement("strong");
  name.textContent = title;
  info.append(name, ...details.map(summaryLine));

  article.append(info);

  if (actions.length > 0) {
    const actionsWrap = document.createElement("div");
    actionsWrap.className = "actions";
    actionsWrap.append(...actions);
    article.append(actionsWrap);
  }
  return article;
};

const renderFriends = () => {
  friendsList.replaceChildren();
  if (state.contacts.friends.length === 0) {
    friendsList.append(mutedBlock("No accepted friends yet."));
    return;
  }

  for (const friend of state.contacts.friends) {
    const removeFriendButton = actionButton("Remove friend", "secondary");
    removeFriendButton.addEventListener("click", async () => {
      await performContactsAction(
        () => jsonRequest(`/api/contacts/${friend.user.id}`, { method: "DELETE" }),
        `Removed ${friend.user.displayName} from accepted friends.`
      );
    });

    const blockButton = actionButton("Block", "danger");
    blockButton.addEventListener("click", async () => {
      await performContactsAction(
        () => jsonRequest(`/api/blocks/${friend.user.id}`, { method: "PUT" }),
        `Blocked ${friend.user.displayName}.`
      );
    });

    friendsList.append(
      contactCard(
        userLabel(friend.user),
        [`Friends since ${formatDate(friend.friendsSince)}`],
        [removeFriendButton, blockButton]
      )
    );
  }
};

const renderInbound = () => {
  inboundRequestsList.replaceChildren();
  if (state.contacts.inboundPendingRequests.length === 0) {
    inboundRequestsList.append(mutedBlock("No inbound pending requests."));
    return;
  }

  for (const request of state.contacts.inboundPendingRequests) {
    const acceptButton = actionButton("Accept");
    acceptButton.addEventListener("click", async () => {
      await performContactsAction(
        () => jsonRequest(`/api/friend-requests/${request.requestId}/accept`, { method: "POST" }),
        "Friend request accepted."
      );
    });

    const rejectButton = actionButton("Reject", "secondary");
    rejectButton.addEventListener("click", async () => {
      await performContactsAction(
        () => jsonRequest(`/api/friend-requests/${request.requestId}/reject`, { method: "POST" }),
        "Friend request rejected."
      );
    });

    const blockButton = actionButton("Block", "danger");
    blockButton.addEventListener("click", async () => {
      await performContactsAction(
        () => jsonRequest(`/api/blocks/${request.user.id}`, { method: "PUT" }),
        `Blocked ${request.user.displayName}.`
      );
    });

    inboundRequestsList.append(
      contactCard(
        userLabel(request.user),
        [
          request.messageText ? `Message: ${request.messageText}` : "No message attached.",
          `Requested ${formatDate(request.createdAt)}`,
        ],
        [acceptButton, rejectButton, blockButton]
      )
    );
  }
};

const renderOutbound = () => {
  outboundRequestsList.replaceChildren();
  if (state.contacts.outboundPendingRequests.length === 0) {
    outboundRequestsList.append(mutedBlock("No outbound pending requests."));
    return;
  }

  for (const request of state.contacts.outboundPendingRequests) {
    const blockButton = actionButton("Block", "danger");
    blockButton.addEventListener("click", async () => {
      await performContactsAction(
        () => jsonRequest(`/api/blocks/${request.user.id}`, { method: "PUT" }),
        `Blocked ${request.user.displayName}.`
      );
    });

    outboundRequestsList.append(
      contactCard(
        userLabel(request.user),
        [
          request.messageText ? `Message: ${request.messageText}` : "No message attached.",
          `Sent ${formatDate(request.createdAt)}`,
        ],
        [blockButton]
      )
    );
  }
};

const renderBlockedUsers = () => {
  blockedUsersList.replaceChildren();
  if (state.contacts.blockedUsers.length === 0) {
    blockedUsersList.append(mutedBlock("No blocked users."));
    return;
  }

  for (const blockedUser of state.contacts.blockedUsers) {
    const unblockButton = actionButton("Unblock", "secondary");
    unblockButton.addEventListener("click", async () => {
      await performContactsAction(
        () => jsonRequest(`/api/blocks/${blockedUser.user.id}`, { method: "DELETE" }),
        `Unblocked ${blockedUser.user.displayName}.`
      );
    });

    blockedUsersList.append(
      contactCard(
        userLabel(blockedUser.user),
        [`Blocked ${formatDate(blockedUser.blockedAt)}`],
        [unblockButton]
      )
    );
  }
};

const render = () => {
  renderFriends();
  renderInbound();
  renderOutbound();
  renderBlockedUsers();
};

const refreshContacts = async ({ preserveMessage = false } = {}) => {
  if (!preserveMessage) {
    clearMessage(contactsMessage);
  }
  state.contacts = await jsonRequest("/api/contacts");
  render();
};

bindAsyncForm(createRequestForm, createRequestMessage, async (formData) => {
  const username = formData.get("username")?.toString().trim() ?? "";
  const userId = formData.get("userId")?.toString().trim() ?? "";
  const messageText = formData.get("messageText")?.toString() ?? "";
  const payload = {};

  if (username) {
    payload.username = username;
  }
  if (userId) {
    payload.userId = userId;
  }
  if (messageText.trim()) {
    payload.messageText = messageText.trim();
  }

  const response = await jsonRequest("/api/friend-requests", {
    method: "POST",
    body: payload,
  });
  createRequestForm.reset();
  writeMessage(
    createRequestMessage,
    "success",
    response.outcome === "AUTO_ACCEPTED"
      ? `Accepted the pending request from ${response.user.displayName}.`
      : `Friend request sent to ${response.user.displayName}.`
  );
  await refreshContacts();
});

refreshContactsButton.addEventListener("click", async () => {
  try {
    await refreshContacts();
    writeMessage(contactsMessage, "success", "Contacts refreshed.");
  } catch (error) {
    writeMessage(contactsMessage, "error", error.message);
  }
});

refreshContacts().catch((error) => {
  writeMessage(contactsMessage, "error", error.message);
});
