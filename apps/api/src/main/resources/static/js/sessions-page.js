import { bindLogoutButton, jsonRequest, writeMessage } from "./common.js";

bindLogoutButton(document.querySelector("[data-logout-button]"));

const listElement = document.querySelector("[data-session-list]");
const message = document.querySelector("[data-sessions-message]");

const formatDate = (value) =>
  new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));

const renderSessions = async () => {
  const sessions = await jsonRequest("/api/sessions");
  listElement.innerHTML = "";

  if (sessions.length === 0) {
    listElement.innerHTML = "<div class='muted-block'>No active sessions remain.</div>";
    return;
  }

  for (const session of sessions) {
    const item = document.createElement("article");
    item.className = `session-item${session.current ? " current" : ""}`;
    item.innerHTML = `
      <div class="split">
        <div>
          <strong>${session.userAgent || "Browser session"}</strong>
          <div class="subtle">IP ${session.ipAddress || "unknown"}</div>
          <div class="subtle">Created ${formatDate(session.createdAt)}</div>
          <div class="subtle">Last seen ${formatDate(session.lastSeenAt)}</div>
        </div>
        <div class="session-actions">
          ${session.current ? "<span class='pill'>Current</span>" : ""}
          <button class="${session.current ? "danger" : "secondary"}">Revoke</button>
        </div>
      </div>
    `;

    item.querySelector("button").addEventListener("click", async () => {
      await jsonRequest(`/api/sessions/${session.id}`, { method: "DELETE" });
      writeMessage(
        message,
        "success",
        session.current ? "Current session revoked. Redirecting to sign in..." : "Selected session revoked."
      );
      if (session.current) {
        window.setTimeout(() => window.location.assign("/login"), 400);
        return;
      }
      await renderSessions();
    });

    listElement.append(item);
  }
};

renderSessions().catch((error) => writeMessage(message, "error", error.message));
