import { bindAsyncForm, jsonRequest, writeMessage } from "./common.js";

const form = document.querySelector("[data-reset-consume-form]");
const message = document.querySelector("[data-form-message]");
const tokenInput = document.querySelector("[data-token-input]");
const tokenPreview = document.querySelector("[data-token-preview]");
const token = new URLSearchParams(window.location.search).get("token") ?? "";

tokenInput.value = token;
tokenPreview.textContent = token ? "Token loaded from the reset link." : "Provide a token from the logged reset URL.";

bindAsyncForm(form, message, async (formData) => {
  await jsonRequest("/api/auth/password/reset", {
    method: "POST",
    body: {
      token: formData.get("token"),
      newPassword: formData.get("newPassword"),
    },
  });

  writeMessage(message, "success", "Password updated. Redirecting to sign in...");
  window.setTimeout(() => window.location.assign("/login"), 700);
});
