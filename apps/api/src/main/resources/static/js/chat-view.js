import { binaryRequest, clearMessage, jsonRequest, multipartRequest, writeMessage } from "./common.js";

const excerpt = (text, maxLength = 120) => {
  if (!text) {
    return "";
  }
  return text.length <= maxLength ? text : `${text.slice(0, maxLength - 1)}…`;
};

const compareMessages = (left, right) => {
  const createdAtComparison = new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime();
  if (createdAtComparison !== 0) {
    return createdAtComparison;
  }
  return left.id.localeCompare(right.id);
};

const authorLabel = (author) => (author.deleted ? author.displayName : `${author.displayName} (@${author.username})`);

const formatBytes = (sizeBytes) => {
  if (sizeBytes < 1024) {
    return `${sizeBytes} B`;
  }
  const units = ["KB", "MB", "GB"];
  let value = sizeBytes / 1024;
  let unitIndex = 0;
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024;
    unitIndex += 1;
  }
  return `${value.toFixed(value >= 10 || unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
};

const messagePreviewText = (message) => {
  if (!message) {
    return "";
  }
  if (message.state === "DELETED") {
    return "Deleted message";
  }
  return excerpt(message.bodyText || "");
};

const actionButton = (label, kind = "secondary") => {
  const button = document.createElement("button");
  button.type = "button";
  button.className = kind;
  button.textContent = label;
  return button;
};

export const createChatSurface = ({
  statusElement,
  emptyElement,
  timelineElement,
  loadOlderButton,
  composerForm,
  bodyInput,
  submitButton,
  attachmentButton,
  attachmentInput,
  replyBanner,
  editBanner,
  cancelReplyButton,
  cancelEditButton,
  formatDate,
  onConversationChanged = async () => {},
  onAccessChanged = async () => {},
}) => {
  const replyBannerCopy = replyBanner.querySelector(".chat-target-banner-copy");
  const editBannerCopy = editBanner.querySelector(".chat-target-banner-copy");
  const defaultPlaceholder = bodyInput.getAttribute("placeholder") ?? "";
  const state = {
    context: null,
    items: [],
    nextBeforeMessageId: null,
    replyTarget: null,
    editTarget: null,
    lastMarkedMessageId: null,
    requestToken: 0,
  };

  const chatPath = () =>
    state.context == null
      ? ""
      : `/api/chats/${state.context.chatType.toLowerCase()}/${encodeURIComponent(state.context.chatId)}`;

  const maybeRefreshForAccessChange = async (error) => {
    if (error?.status === 403 || error?.status === 404) {
      try {
        await onAccessChanged({ error, context: state.context });
      } catch {
      }
    }
  };

  const resetAttachmentInput = () => {
    if (attachmentInput) {
      attachmentInput.value = "";
    }
  };

  const triggerBrowserDownload = (blob, filename) => {
    const downloadUrl = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = downloadUrl;
    link.download = filename;
    document.body.append(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(downloadUrl), 0);
  };

  const canEdit = (message) =>
    state.context != null
    && message.state !== "DELETED"
    && message.author.id === state.context.currentUserId;

  const canDelete = (message) =>
    state.context != null
    && message.state !== "DELETED"
    && (
      message.author.id === state.context.currentUserId
      || (state.context.chatType === "ROOM" && ["OWNER", "ADMIN"].includes(state.context.viewerRole ?? ""))
    );

  const clearComposerState = ({ resetInput = true } = {}) => {
    state.replyTarget = null;
    state.editTarget = null;
    if (resetInput) {
      composerForm.reset();
    }
    resetAttachmentInput();
    replyBanner.hidden = true;
    editBanner.hidden = true;
    submitButton.textContent = "Send message";
  };

  const renderBanners = () => {
    if (state.replyTarget == null) {
      replyBanner.hidden = true;
    } else {
      replyBannerCopy.textContent = `Replying to ${authorLabel(state.replyTarget.author)}: ${messagePreviewText(state.replyTarget)}`;
      replyBanner.hidden = false;
    }

    if (state.editTarget == null) {
      editBanner.hidden = true;
      if (state.replyTarget == null) {
        submitButton.textContent = "Send message";
      }
    } else {
      editBannerCopy.textContent = `Editing your message: ${messagePreviewText(state.editTarget)}`;
      editBanner.hidden = false;
      submitButton.textContent = "Save edit";
    }
  };

  const renderTimeline = () => {
    timelineElement.replaceChildren();
    emptyElement.hidden = state.items.length > 0;
    loadOlderButton.hidden = state.nextBeforeMessageId == null;

    for (const message of state.items) {
      const article = document.createElement("article");
      article.className = "timeline-message";
      if (message.author.id === state.context?.currentUserId) {
        article.classList.add("timeline-message--mine");
      }
      if (message.state === "DELETED") {
        article.classList.add("timeline-message--deleted");
      }

      const header = document.createElement("div");
      header.className = "timeline-message-header";

      const authorWrap = document.createElement("div");
      const author = document.createElement("strong");
      author.textContent = authorLabel(message.author);
      authorWrap.append(author);
      if (message.author.deleted) {
        const tombstoneNote = document.createElement("div");
        tombstoneNote.className = "subtle";
        tombstoneNote.textContent = "Tombstoned account";
        authorWrap.append(tombstoneNote);
      }

      const meta = document.createElement("div");
      meta.className = "timeline-message-meta";

      const timestamp = document.createElement("span");
      timestamp.className = "subtle";
      timestamp.textContent = formatDate(message.createdAt);
      meta.append(timestamp);

      if (message.state === "EDITED") {
        const editedPill = document.createElement("span");
        editedPill.className = "pill neutral";
        editedPill.textContent = "Edited";
        meta.append(editedPill);
      }
      if (message.state === "DELETED") {
        const deletedPill = document.createElement("span");
        deletedPill.className = "pill neutral";
        deletedPill.textContent = "Deleted";
        meta.append(deletedPill);
      }

      header.append(authorWrap, meta);
      article.append(header);

      if (message.replyTo) {
        const replyPreview = document.createElement("div");
        replyPreview.className = "message-quote";
        const replyAuthor = document.createElement("strong");
        replyAuthor.textContent = authorLabel(message.replyTo.author);
        const replyText = document.createElement("div");
        replyText.className = "subtle";
        replyText.textContent = messagePreviewText(message.replyTo);
        replyPreview.append(replyAuthor, replyText);
        article.append(replyPreview);
      }

      if (message.state === "DELETED") {
        const body = document.createElement("div");
        body.className = "timeline-message-body";
        body.textContent = "Message deleted.";
        article.append(body);
      } else if ((message.attachments?.length ?? 0) === 0) {
        const body = document.createElement("div");
        body.className = "timeline-message-body";
        body.textContent = message.bodyText;
        article.append(body);
      }

      if (message.state !== "DELETED" && (message.attachments?.length ?? 0) > 0) {
        const attachmentsWrap = document.createElement("div");
        attachmentsWrap.className = "message-attachments";

        for (const attachment of message.attachments) {
          const attachmentCard = document.createElement("div");
          attachmentCard.className = "message-attachment";

          const attachmentHeader = document.createElement("div");
          attachmentHeader.className = "message-attachment-header";

          const attachmentInfo = document.createElement("div");
          const attachmentName = document.createElement("strong");
          attachmentName.textContent = attachment.originalName;
          const attachmentMeta = document.createElement("div");
          attachmentMeta.className = "subtle";
          attachmentMeta.textContent = `${formatBytes(attachment.sizeBytes)} · ${attachment.mediaType}`;
          attachmentInfo.append(attachmentName, attachmentMeta);

          const attachmentActions = document.createElement("div");
          attachmentActions.className = "actions";
          const downloadButton = actionButton("Download", "secondary");
          downloadButton.addEventListener("click", async () => {
            downloadButton.disabled = true;
            try {
              const { blob } = await binaryRequest(`/api/attachments/${encodeURIComponent(attachment.attachmentId)}/download`);
              triggerBrowserDownload(blob, attachment.originalName);
              writeMessage(statusElement, "success", `Downloaded ${attachment.originalName}.`);
            } catch (error) {
              await maybeRefreshForAccessChange(error);
              writeMessage(statusElement, "error", error.message);
            } finally {
              downloadButton.disabled = false;
            }
          });
          attachmentActions.append(downloadButton);

          attachmentHeader.append(attachmentInfo, attachmentActions);
          attachmentCard.append(attachmentHeader);

          if (attachment.commentText) {
            const attachmentComment = document.createElement("div");
            attachmentComment.className = "message-attachment-comment";
            attachmentComment.textContent = attachment.commentText;
            attachmentCard.append(attachmentComment);
          }

          attachmentsWrap.append(attachmentCard);
        }

        article.append(attachmentsWrap);
      }

      const actions = document.createElement("div");
      actions.className = "actions";

      const replyButton = actionButton("Reply");
      replyButton.addEventListener("click", () => {
        state.editTarget = null;
        state.replyTarget = message;
        renderBanners();
        bodyInput.focus();
      });
      actions.append(replyButton);

      if (canEdit(message)) {
        const editButton = actionButton("Edit");
        editButton.addEventListener("click", () => {
          state.replyTarget = null;
          state.editTarget = message;
          bodyInput.value = message.bodyText;
          renderBanners();
          bodyInput.focus();
        });
        actions.append(editButton);
      }

      if (canDelete(message)) {
        const deleteButton = actionButton("Delete", "danger");
        deleteButton.addEventListener("click", async () => {
          const confirmed = window.confirm("Delete this message?");
          if (!confirmed) {
            return;
          }
          try {
            const deleted = await jsonRequest(`/api/messages/${encodeURIComponent(message.id)}`, { method: "DELETE" });
            state.items = state.items.map((item) => (item.id === deleted.id ? deleted : item));
            if (state.replyTarget?.id === deleted.id) {
              state.replyTarget = deleted;
            }
            if (state.editTarget?.id === deleted.id) {
              clearComposerState();
            }
            renderBanners();
            renderTimeline();
            await advanceReadMarker();
            await onConversationChanged({ reason: "delete", chatId: state.context?.chatId });
            writeMessage(statusElement, "success", "Message deleted.");
          } catch (error) {
            await maybeRefreshForAccessChange(error);
            writeMessage(statusElement, "error", error.message);
          }
        });
        actions.append(deleteButton);
      }

      article.append(actions);
      timelineElement.append(article);
    }
  };

  const loadLatest = async () => {
    if (state.context == null) {
      return;
    }
    const token = ++state.requestToken;
    clearMessage(statusElement);
    const page = await jsonRequest(`${chatPath()}/messages`);
    if (token !== state.requestToken) {
      return;
    }
    state.items = [...page.items].sort(compareMessages);
    state.nextBeforeMessageId = page.nextBeforeMessageId;
    renderTimeline();
    await advanceReadMarker();
  };

  const loadOlder = async () => {
    if (state.context == null || state.nextBeforeMessageId == null) {
      return;
    }
    loadOlderButton.disabled = true;
    try {
      const page = await jsonRequest(`${chatPath()}/messages?before=${encodeURIComponent(state.nextBeforeMessageId)}`);
      const merged = [...page.items, ...state.items];
      state.items = merged.sort(compareMessages);
      state.nextBeforeMessageId = page.nextBeforeMessageId;
      renderTimeline();
    } catch (error) {
      await maybeRefreshForAccessChange(error);
      writeMessage(statusElement, "error", error.message);
    } finally {
      loadOlderButton.disabled = false;
    }
  };

  const advanceReadMarker = async () => {
    if (state.context == null || state.items.length === 0) {
      return;
    }
    const newestMessageId = state.items.at(-1).id;
    if (state.lastMarkedMessageId === newestMessageId) {
      return;
    }
    await jsonRequest(`${chatPath()}/read-markers`, {
      method: "POST",
      body: { lastReadMessageId: newestMessageId },
    });
    state.lastMarkedMessageId = newestMessageId;
    await onConversationChanged({ reason: "read", chatId: state.context.chatId });
  };

  const submitMessage = async (event) => {
    event.preventDefault();
    if (state.context == null) {
      return;
    }

    const bodyText = bodyInput.value.trim();
    if (!bodyText) {
      writeMessage(statusElement, "error", "Message body is required.");
      return;
    }

    submitButton.disabled = true;
    clearMessage(statusElement);
    try {
      if (state.editTarget) {
        const edited = await jsonRequest(`/api/messages/${encodeURIComponent(state.editTarget.id)}`, {
          method: "PATCH",
          body: { bodyText },
        });
        state.items = state.items.map((item) => (item.id === edited.id ? edited : item));
        clearComposerState();
        renderBanners();
        renderTimeline();
        await advanceReadMarker();
        await onConversationChanged({ reason: "edit", chatId: state.context.chatId });
        writeMessage(statusElement, "success", "Message updated.");
      } else {
        const created = await jsonRequest(`${chatPath()}/messages`, {
          method: "POST",
          body: {
            bodyText,
            parentMessageId: state.replyTarget?.id ?? null,
          },
        });
        state.items = [...state.items, created].sort(compareMessages);
        clearComposerState();
        renderBanners();
        renderTimeline();
        await advanceReadMarker();
        await onConversationChanged({ reason: "send", chatId: state.context.chatId });
        writeMessage(statusElement, "success", "Message sent.");
      }
    } catch (error) {
      await maybeRefreshForAccessChange(error);
      writeMessage(statusElement, "error", error.message);
    } finally {
      submitButton.disabled = false;
    }
  };

  const uploadAttachment = async (file, { source = "upload" } = {}) => {
    if (state.context == null || !file) {
      return;
    }
    if (state.replyTarget || state.editTarget) {
      writeMessage(statusElement, "error", "Finish or cancel reply or edit mode before uploading an attachment.");
      resetAttachmentInput();
      return;
    }

    const commentText = bodyInput.value.trim();
    submitButton.disabled = true;
    if (attachmentButton) {
      attachmentButton.disabled = true;
    }
    clearMessage(statusElement);

    try {
      const formData = new FormData();
      formData.append("file", file, file.name || "attachment");
      if (commentText) {
        formData.append("commentText", commentText);
      }
      const descriptor = await multipartRequest(`${chatPath()}/attachments`, {
        method: "POST",
        formData,
      });
      clearComposerState();
      renderBanners();
      await loadLatest();
      await onConversationChanged({ reason: "attachment", chatId: state.context.chatId });
      writeMessage(
        statusElement,
        "success",
        source === "paste"
          ? `Pasted image uploaded as ${descriptor.originalName}.`
          : `Uploaded ${descriptor.originalName}.`
      );
    } catch (error) {
      await maybeRefreshForAccessChange(error);
      writeMessage(statusElement, "error", error.message);
    } finally {
      submitButton.disabled = false;
      if (attachmentButton) {
        attachmentButton.disabled = false;
      }
      resetAttachmentInput();
    }
  };

  loadOlderButton.addEventListener("click", loadOlder);
  composerForm.addEventListener("submit", submitMessage);
  attachmentButton?.addEventListener("click", () => {
    if (state.context == null) {
      writeMessage(statusElement, "error", "Open a conversation before uploading attachments.");
      return;
    }
    attachmentInput?.click();
  });
  attachmentInput?.addEventListener("change", async () => {
    const file = attachmentInput.files?.[0];
    await uploadAttachment(file);
  });
  bodyInput.addEventListener("paste", async (event) => {
    const imageItem = [...(event.clipboardData?.items ?? [])].find((item) => item.kind === "file" && item.type.startsWith("image/"));
    if (!imageItem) {
      return;
    }
    const file = imageItem.getAsFile();
    if (!file) {
      return;
    }
    event.preventDefault();
    await uploadAttachment(file, { source: "paste" });
  });
  cancelReplyButton.addEventListener("click", () => {
    state.replyTarget = null;
    renderBanners();
  });
  cancelEditButton.addEventListener("click", () => {
    clearComposerState();
    renderBanners();
  });

  return {
    async open(context) {
      state.context = { ...context };
      state.items = [];
      state.nextBeforeMessageId = null;
      state.lastMarkedMessageId = null;
      bodyInput.setAttribute("placeholder", context.placeholder ?? defaultPlaceholder);
      if (attachmentButton) {
        attachmentButton.disabled = false;
      }
      clearComposerState();
      renderBanners();
      renderTimeline();
      await loadLatest();
    },
    clear() {
      state.context = null;
      state.items = [];
      state.nextBeforeMessageId = null;
      state.lastMarkedMessageId = null;
      clearComposerState();
      renderBanners();
      renderTimeline();
      clearMessage(statusElement);
      bodyInput.setAttribute("placeholder", defaultPlaceholder);
      if (attachmentButton) {
        attachmentButton.disabled = true;
      }
    },
    async refresh() {
      await loadLatest();
    },
  };
};
