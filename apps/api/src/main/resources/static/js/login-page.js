import { bindAsyncForm, consumeFlashMessage, jsonRequest, writeMessage } from "./common.js";

const form = document.querySelector("[data-login-form]");
const message = document.querySelector("[data-form-message]");

const flashMessage = consumeFlashMessage();
if (flashMessage) {
  writeMessage(message, flashMessage.kind, flashMessage.message);
}

bindAsyncForm(form, message, async (formData) => {
  await jsonRequest("/api/auth/login", {
    method: "POST",
    body: {
      email: formData.get("email"),
      password: formData.get("password"),
    },
  });

  writeMessage(message, "success", "Signed in. Redirecting to the chat shell...");
  window.setTimeout(() => window.location.assign("/app"), 300);
});
