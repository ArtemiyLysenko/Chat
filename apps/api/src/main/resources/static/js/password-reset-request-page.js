import { bindAsyncForm, jsonRequest, writeMessage } from "./common.js";

const form = document.querySelector("[data-reset-request-form]");
const message = document.querySelector("[data-form-message]");

bindAsyncForm(form, message, async (formData) => {
  await jsonRequest("/api/auth/password/reset-requests", {
    method: "POST",
    body: {
      email: formData.get("email"),
    },
  });

  writeMessage(
    message,
    "success",
    "If that account exists, a reset link has been issued and logged by the local server."
  );
});
