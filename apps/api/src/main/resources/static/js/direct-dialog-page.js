import { bindLogoutButton, jsonRequest, writeMessage } from "./common.js";

bindLogoutButton(document.querySelector("[data-logout-button]"));

const titleElement = document.querySelector("[data-dialog-title]");
const subtitleElement = document.querySelector("[data-dialog-subtitle]");
const messageElement = document.querySelector("[data-dialog-message]");
const participantElement = document.querySelector("[data-dialog-participant]");
const identityElement = document.querySelector("[data-dialog-identity]");
const placeholderElement = document.querySelector("[data-dialog-placeholder]");

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

const formatDate = (value) =>
  new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));

const participantUserId = () => {
  const segments = window.location.pathname.split("/").filter(Boolean);
  return segments.at(-1) ?? "";
};

const loadDialog = async () => {
  const userId = participantUserId();
  if (!userId) {
    writeMessage(messageElement, "error", "Participant id is missing from the route.");
    titleElement.textContent = "Direct dialog unavailable";
    return;
  }

  try {
    const dialog = await jsonRequest(`/api/direct-dialogs/${userId}`, { method: "POST" });
    titleElement.textContent = `Direct dialog with ${dialog.participant.displayName}`;
    subtitleElement.textContent = dialog.created
      ? "A new stable direct-dialog identity was created for this friend pair."
      : "An existing stable direct-dialog identity was reused for this friend pair.";
    writeMessage(
      messageElement,
      "success",
      dialog.created ? "Direct dialog placeholder created." : "Existing direct dialog placeholder loaded."
    );

    participantElement.replaceChildren(
      detailLine("Display name", dialog.participant.displayName),
      detailLine("Username", `@${dialog.participant.username}`),
      detailLine("User id", dialog.participant.id),
    );
    identityElement.replaceChildren(
      detailLine("Dialog id", dialog.dialogId),
      detailLine("Chat reference", `DIRECT:${dialog.dialogId}`),
      detailLine("Established", formatDate(dialog.createdAt)),
    );
    placeholderElement.textContent = "Messaging controls, history, and unread indicators are intentionally absent in this milestone.";
  } catch (error) {
    titleElement.textContent = "Direct dialog unavailable";
    participantElement.replaceChildren(mutedBlock("No participant details are available."));
    identityElement.replaceChildren(mutedBlock("No dialog identity was ensured."));
    placeholderElement.textContent = "This placeholder page only activates when the pair is currently eligible for direct messaging.";
    writeMessage(messageElement, "error", error.message);
  }
};

loadDialog();
