const FLASH_MESSAGE_STORAGE_KEY = "chat.flash.message";

export const setFlashMessage = (kind, message) => {
  try {
    window.sessionStorage.setItem(FLASH_MESSAGE_STORAGE_KEY, JSON.stringify({ kind, message }));
  } catch {
  }
};

export const consumeFlashMessage = () => {
  try {
    const payload = window.sessionStorage.getItem(FLASH_MESSAGE_STORAGE_KEY);
    if (!payload) {
      return null;
    }
    window.sessionStorage.removeItem(FLASH_MESSAGE_STORAGE_KEY);
    return JSON.parse(payload);
  } catch {
    try {
      window.sessionStorage.removeItem(FLASH_MESSAGE_STORAGE_KEY);
    } catch {
    }
    return null;
  }
};

export const redirectToLogin = (message = "This session is no longer active. Sign in again.") => {
  setFlashMessage("error", message);
  if (window.location.pathname === "/login") {
    return;
  }
  window.location.assign("/login");
};

const redirectIfUnauthorized = (response) => {
  if (response.status === 401) {
    redirectToLogin("This session is no longer active. Sign in again.");
    return true;
  }
  return false;
};

export const readCookie = (name) =>
  document.cookie
    .split(";")
    .map((part) => part.trim())
    .find((part) => part.startsWith(`${name}=`))
    ?.slice(name.length + 1) ?? "";

export const csrfToken = () => readCookie("XSRF-TOKEN");

export const writeMessage = (element, kind, message) => {
  element.className = `message ${kind}`;
  element.textContent = message;
};

export const clearMessage = (element) => {
  element.className = "message";
  element.textContent = "";
};

export const jsonRequest = async (url, { method = "GET", body } = {}) => {
  const headers = {};
  const options = { method, headers, credentials: "same-origin" };

  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
    options.body = JSON.stringify(body);
  }

  if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
    headers["X-CSRF-TOKEN"] = csrfToken();
  }

  const response = await fetch(url, options);
  if (redirectIfUnauthorized(response)) {
    throw new Error("Unauthorized");
  }

  const contentType = response.headers.get("content-type") ?? "";
  const payload = contentType.includes("application/json") ? await response.json() : null;
  if (!response.ok) {
    const error = new Error(payload?.message ?? `Request failed with status ${response.status}.`);
    error.status = response.status;
    error.code = payload?.code ?? null;
    throw error;
  }
  return payload;
};

export const multipartRequest = async (url, { method = "POST", formData } = {}) => {
  const headers = {};
  const options = {
    method,
    headers,
    credentials: "same-origin",
    body: formData,
  };

  if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
    headers["X-CSRF-TOKEN"] = csrfToken();
  }

  const response = await fetch(url, options);
  if (redirectIfUnauthorized(response)) {
    throw new Error("Unauthorized");
  }

  const contentType = response.headers.get("content-type") ?? "";
  const payload = contentType.includes("application/json") ? await response.json() : null;
  if (!response.ok) {
    const error = new Error(payload?.message ?? `Request failed with status ${response.status}.`);
    error.status = response.status;
    error.code = payload?.code ?? null;
    throw error;
  }
  return payload;
};

export const binaryRequest = async (url) => {
  const response = await fetch(url, { credentials: "same-origin" });
  if (redirectIfUnauthorized(response)) {
    throw new Error("Unauthorized");
  }

  const contentType = response.headers.get("content-type") ?? "";
  if (!response.ok) {
    const payload = contentType.includes("application/json") ? await response.json() : null;
    const error = new Error(payload?.message ?? `Request failed with status ${response.status}.`);
    error.status = response.status;
    error.code = payload?.code ?? null;
    throw error;
  }

  return {
    blob: await response.blob(),
    headers: response.headers,
  };
};

export const bindAsyncForm = (form, messageElement, handler) => {
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    clearMessage(messageElement);

    const submitButton = form.querySelector("button[type='submit']");
    submitButton.disabled = true;
    try {
      await handler(new FormData(form));
    }
    catch (error) {
      writeMessage(messageElement, "error", error.message);
    }
    finally {
      submitButton.disabled = false;
    }
  });
};

export const bindLogoutButton = (button) => {
  if (!button) {
    return;
  }
  button.addEventListener("click", async () => {
    button.disabled = true;
    try {
      await jsonRequest("/api/auth/logout", { method: "POST" });
    }
    finally {
      window.location.assign("/login");
    }
  });
};

export const createCoalescedTask = (task) => {
  let running = null;
  let queued = false;

  const run = async () => {
    do {
      queued = false;
      await task();
    } while (queued);
  };

  return async () => {
    if (running) {
      queued = true;
      return running;
    }
    running = run().finally(() => {
      running = null;
    });
    return running;
  };
};
