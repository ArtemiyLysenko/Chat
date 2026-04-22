import { bindLogoutButton, clearMessage, createCoalescedTask, jsonRequest, writeMessage } from "./common.js";

bindLogoutButton(document.querySelector("[data-logout-button]"));

const refreshButton = document.querySelector("[data-refresh-button]");
const statusMessage = document.querySelector("[data-status-message]");
const peerCount = document.querySelector("[data-peer-count]");
const upCount = document.querySelector("[data-up-count]");
const downCount = document.querySelector("[data-down-count]");
const sampleCount = document.querySelector("[data-sample-count]");
const peerList = document.querySelector("[data-peer-list]");
const trafficList = document.querySelector("[data-traffic-list]");

const state = {
  peers: [],
  traffic: [],
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

const peerCard = (peer) => {
  const article = document.createElement("article");
  article.className = "room-card";

  const top = document.createElement("div");
  top.className = "split";

  const info = document.createElement("div");
  const title = document.createElement("strong");
  title.textContent = peer.peerDomain;
  info.append(
    title,
    subtleLine(peer.lastConnectedAt ? `Last connected ${formatDate(peer.lastConnectedAt)}` : "No successful federation delivery recorded yet."),
    subtleLine(peer.lastErrorAt ? `Last error ${formatDate(peer.lastErrorAt)}` : "No federation error recorded.")
  );

  top.append(info, statusPill(peer.status));
  article.append(top);
  return article;
};

const trafficCard = (sample) => {
  const article = document.createElement("article");
  article.className = "room-card";

  const top = document.createElement("div");
  top.className = "split";

  const info = document.createElement("div");
  const title = document.createElement("strong");
  title.textContent = sample.peerDomain;
  info.append(
    title,
    subtleLine(`Sampled ${formatDate(sample.sampledAt)}`),
    subtleLine(`Messages in ${sample.inboundMessages} · out ${sample.outboundMessages}`),
    subtleLine(`Stanzas in ${sample.inboundStanzas} · out ${sample.outboundStanzas}`),
    subtleLine(`Errors ${sample.errorCount}`)
  );

  top.append(info, statusPill(sample.errorCount > 0 ? "DEGRADED" : "UP"));
  article.append(top);
  return article;
};

const renderList = (element, items, renderer, emptyText) => {
  element.replaceChildren();
  if (items.length === 0) {
    element.append(mutedBlock(emptyText));
    return;
  }
  for (const item of items) {
    element.append(renderer(item));
  }
};

const render = () => {
  peerCount.textContent = String(state.peers.length);
  upCount.textContent = String(state.peers.filter((peer) => peer.status === "UP").length);
  downCount.textContent = String(state.peers.filter((peer) => peer.status === "DOWN").length);
  sampleCount.textContent = String(state.traffic.length);

  renderList(peerList, state.peers, peerCard, "No federation peers are currently tracked.");
  renderList(trafficList, state.traffic, trafficCard, "No federation traffic samples are currently available.");
};

const refreshFederation = createCoalescedTask(async () => {
  try {
    const [peers, traffic] = await Promise.all([
      jsonRequest("/api/admin/jabber/federation/peers"),
      jsonRequest("/api/admin/jabber/federation/traffic"),
    ]);
    state.peers = Array.isArray(peers) ? peers : [];
    state.traffic = Array.isArray(traffic) ? traffic : [];
    clearMessage(statusMessage);
    render();
  } catch (error) {
    writeMessage(statusMessage, "error", error.message);
  }
});

refreshButton.addEventListener("click", refreshFederation);

await refreshFederation();
