import { bindAsyncForm, jsonRequest, writeMessage } from "./common.js";

const form = document.querySelector("[data-register-form]");
const message = document.querySelector("[data-form-message]");

bindAsyncForm(form, message, async (formData) => {
  await jsonRequest("/api/auth/register", {
    method: "POST",
    body: {
      email: formData.get("email"),
      username: formData.get("username"),
      password: formData.get("password"),
    },
  });

  writeMessage(message, "success", "Account created. Redirecting to sign in...");
  window.setTimeout(() => window.location.assign("/login"), 700);
});
