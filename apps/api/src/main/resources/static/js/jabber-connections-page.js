import { bindLogoutButton, clearMessage, createCoalescedTask, jsonRequest, writeMessage } from "./common.js";

bindLogoutButton(document.querySelector("[data-logout-button]"));

const refreshButton = document.querySelector("[data-refresh-button]");
const statusMessage = document.querySelector("[data-status-message]");
const totalCount = document.querySelector("[data-total-count]");
const activeCount = document.querySelector("[data-active-count]");
const recentCount = document.querySelector("[data-recent-count]");
const errorCount = document.querySelector("[data-error-count]");
const activeList = document.querySelector("[data-active-list]");
const recentList = document.querySelector("[data-recent-list]");

const state = {
  connections: [],
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

const subtleLine = (text) => {
  const element = document.createElement("div");
  element.className = "subtle";
  element.textContent = text;
  return element;
};

const statusPill = (status) => {
  const element = document.createElement("span");
  const normalized = (status ?? "UNKNOWN").toLowerCase();
  element.className = `pill status-${normalized}`;
  element.textContent = status ?? "UNKNOWN";
  return element;
};

const isActive = (connection) => connection.status === "AUTHENTICATING" || connection.status === "CONNECTED";

const connectionCard = (connection) => {
  const article = document.createElement("article");
  article.className = "session-item";

  const top = document.createElement("div");
  top.className = "split";

  const info = document.createElement("div");
  const title = document.createElement("strong");
  title.textContent = connection.principal || connection.sessionId;
  info.append(
    title,
    subtleLine(`Connected ${formatDate(connection.connectedAt)}`),
    subtleLine(`Session ${connection.sessionId}`),
    subtleLine(`Remote address ${connection.remoteAddress || "unavailable"}`)
  );

  top.append(info, statusPill(connection.status));
  article.append(top);
  return article;
};

const renderList = (element, connections, emptyText) => {
  element.replaceChildren();
  if (connections.length === 0) {
    element.append(mutedBlock(emptyText));
    return;
  }
  for (const connection of connections) {
    element.append(connectionCard(connection));
  }
};

const render = () => {
  const activeConnections = state.connections.filter(isActive);
  const recentConnections = state.connections.filter((connection) => !isActive(connection));

  totalCount.textContent = String(state.connections.length);
  activeCount.textContent = String(activeConnections.length);
  recentCount.textContent = String(recentConnections.length);
  errorCount.textContent = String(state.connections.filter((connection) => connection.status === "ERROR").length);

  renderList(activeList, activeConnections, "No active XMPP client sessions are currently tracked.");
  renderList(recentList, recentConnections, "No recent disconnected or errored XMPP sessions are available yet.");
};

const refreshConnections = createCoalescedTask(async () => {
  try {
    const connections = await jsonRequest("/api/admin/jabber/connections");
    state.connections = Array.isArray(connections) ? connections : [];
    clearMessage(statusMessage);
    render();
  } catch (error) {
    writeMessage(statusMessage, "error", error.message);
  }
});

refreshButton.addEventListener("click", refreshConnections);

await refreshConnections();
