import { bindAsyncForm, bindLogoutButton, jsonRequest, writeMessage } from "./common.js";

bindLogoutButton(document.querySelector("[data-logout-button]"));

const changePasswordForm = document.querySelector("[data-password-change-form]");
const changePasswordMessage = document.querySelector("[data-password-change-message]");

bindAsyncForm(changePasswordForm, changePasswordMessage, async (formData) => {
  await jsonRequest("/api/auth/password/change", {
    method: "POST",
    body: {
      currentPassword: formData.get("currentPassword"),
      newPassword: formData.get("newPassword"),
    },
  });

  changePasswordForm.reset();
  writeMessage(changePasswordMessage, "success", "Password changed. Other sessions were revoked.");
});

const deleteAccountForm = document.querySelector("[data-account-delete-form]");
const deleteAccountMessage = document.querySelector("[data-account-delete-message]");

bindAsyncForm(deleteAccountForm, deleteAccountMessage, async (formData) => {
  const confirmed = window.confirm(
    "Delete this account now? This revokes every session and tombstones the identity."
  );
  if (!confirmed) {
    return;
  }

  await jsonRequest("/api/account", {
    method: "DELETE",
    body: {
      currentPassword: formData.get("currentPassword"),
    },
  });

  writeMessage(deleteAccountMessage, "success", "Account deleted. Redirecting...");
  window.setTimeout(() => window.location.assign("/"), 500);
});
