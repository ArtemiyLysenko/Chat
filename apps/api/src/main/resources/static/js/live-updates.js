import { redirectToLogin } from "./common.js";

const SESSION_REVOKED_CLOSE_CODE = 4401;
const MAX_RECONNECT_DELAY_MILLIS = 5_000;

const websocketUrl = () => {
  const protocol = window.location.protocol === "https:" ? "wss" : "ws";
  return `${protocol}://${window.location.host}/ws`;
};

const reconnectDelayMillis = (attempt) =>
  Math.min(MAX_RECONNECT_DELAY_MILLIS, 500 * (2 ** Math.min(attempt, 4)));

const verifySessionStillActive = async () => {
  try {
    const response = await fetch("/api/sessions", {
      credentials: "same-origin",
      headers: {
        Accept: "application/json",
      },
    });
    return response.status !== 401;
  } catch {
    return true;
  }
};

export const createLiveUpdatesClient = ({
  onEvent = async () => {},
  onReconnect = async () => {},
  onSessionRevoked = () => redirectToLogin("This session was revoked. Sign in again."),
} = {}) => {
  const state = {
    socket: null,
    reconnectTimer: null,
    reconnectAttempt: 0,
    lastEventId: "",
    stopped: false,
    connectedOnce: false,
    revoking: false,
  };

  const stop = ({ closeSocket = true } = {}) => {
    state.stopped = true;
    if (state.reconnectTimer != null) {
      window.clearTimeout(state.reconnectTimer);
      state.reconnectTimer = null;
    }
    const activeSocket = state.socket;
    state.socket = null;
    if (closeSocket && activeSocket != null && activeSocket.readyState === WebSocket.OPEN) {
      activeSocket.close(1000, "Page closed.");
    }
  };

  const handleSessionRevoked = () => {
    if (state.revoking) {
      return;
    }
    state.revoking = true;
    stop({ closeSocket: false });
    onSessionRevoked();
  };

  const scheduleReconnect = async () => {
    if (state.stopped || state.revoking || state.reconnectTimer != null) {
      return;
    }
    const sessionStillActive = await verifySessionStillActive();
    if (!sessionStillActive) {
      handleSessionRevoked();
      return;
    }

    const delay = reconnectDelayMillis(state.reconnectAttempt);
    state.reconnectAttempt += 1;
    state.reconnectTimer = window.setTimeout(() => {
      state.reconnectTimer = null;
      connect();
    }, delay);
  };

  const connect = () => {
    if (state.stopped || state.revoking) {
      return;
    }

    const socket = new WebSocket(websocketUrl());
    state.socket = socket;

    socket.addEventListener("open", async () => {
      if (state.socket !== socket || state.stopped) {
        socket.close(1000, "Superseded.");
        return;
      }

      const resumedConnection = state.connectedOnce;
      state.connectedOnce = true;
      state.reconnectAttempt = 0;

      if (state.lastEventId) {
        socket.send(JSON.stringify({
          type: "subscription.resume",
          lastEventId: state.lastEventId,
        }));
      }

      if (resumedConnection) {
        try {
          await onReconnect();
        } catch (error) {
          console.error("Reconnect refresh failed.", error);
        }
      }
    });

    socket.addEventListener("message", async (event) => {
      if (state.socket !== socket || state.stopped || state.revoking) {
        return;
      }

      let envelope;
      try {
        envelope = JSON.parse(event.data);
      } catch (error) {
        console.error("Unable to parse live event.", error);
        return;
      }

      if (typeof envelope?.eventId === "string") {
        state.lastEventId = envelope.eventId;
      }

      if (envelope?.type === "session.revoked") {
        handleSessionRevoked();
        return;
      }

      try {
        await onEvent(envelope);
      } catch (error) {
        console.error("Live event handling failed.", error);
      }
    });

    socket.addEventListener("close", async (event) => {
      if (state.socket === socket) {
        state.socket = null;
      }
      if (state.stopped || state.revoking) {
        return;
      }
      if (event.code === SESSION_REVOKED_CLOSE_CODE) {
        handleSessionRevoked();
        return;
      }
      await scheduleReconnect();
    });
  };

  window.addEventListener("pagehide", () => {
    stop();
  }, { once: true });

  connect();

  return {
    stop,
  };
};
