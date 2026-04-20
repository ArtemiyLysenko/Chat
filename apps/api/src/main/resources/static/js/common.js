const redirectIfUnauthorized = (response) => {
  if (response.status === 401) {
    window.location.assign("/login");
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
    throw new Error(payload?.message ?? `Request failed with status ${response.status}.`);
  }
  return payload;
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
