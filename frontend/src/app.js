import {
  createSecretsApi,
  createSecretsStore,
  formatTimestamp,
  parseTags,
  toCreateSecretPayload,
  toUpdateSecretPayload,
  validateAclAccount,
  validateAclPermissions,
  validateSecretDraft
} from "./core.js";

export function createApp(options = {}) {
  const documentRef = options.document ?? globalThis.document;
  const store = options.store ?? createSecretsStore(createSecretsApi());

  const elements = {
    list: documentRef.querySelector("[data-secret-list]"),
    listStatus: documentRef.querySelector("[data-list-status]"),
    detailPanel: documentRef.querySelector("[data-detail-panel]"),
    createForm: documentRef.querySelector("[data-create-form]"),
    createError: documentRef.querySelector("[data-create-error]"),
    refresh: documentRef.querySelector("[data-refresh]"),
    search: documentRef.querySelector("[data-search]"),
    toast: documentRef.querySelector("[data-toast]")
  };

  let currentState = store.getState();
  let aclState = createAclState();

  function render() {
    elements.listStatus.textContent = currentState.loading ? "Loading secrets..." : currentState.error;
    renderList();
    renderDetail();
  }

  function renderList() {
    const query = elements.search.value.trim().toLowerCase();
    const secrets = currentState.secrets.filter((secret) => {
      const haystack = `${secret.name} ${secret.description ?? ""} ${(secret.tags ?? []).join(" ")}`.toLowerCase();
      return haystack.includes(query);
    });

    elements.list.replaceChildren(...secrets.map((secret) => {
      const button = documentRef.createElement("button");
      button.type = "button";
      button.className = secret.id === currentState.selectedId ? "secret-row selected" : "secret-row";
      button.addEventListener("click", () => store.select(secret.id));
      button.innerHTML = `
      <span class="secret-name">${escapeHtml(secret.name)}</span>
      <span class="secret-meta">${escapeHtml(secret.description || "No description")}</span>
      <span class="tag-strip">${(secret.tags ?? []).slice(0, 3).map((tag) => `<span>${escapeHtml(tag)}</span>`).join("")}</span>
    `;
      const item = documentRef.createElement("li");
      item.append(button);
      return item;
    }));

    if (!currentState.loading && !currentState.error && secrets.length === 0) {
      elements.listStatus.textContent = query ? "No matching secrets." : "No secrets yet.";
    }
  }

  function renderDetail() {
    const secret = currentState.selected;
    if (!secret) {
      renderEmptyDetail();
      return;
    }

    aclState = ensureAclStateForSecret(aclState, secret.id);
    elements.detailPanel.innerHTML = buildDetailMarkup(secret, aclState);

    elements.detailPanel.querySelector("[data-update-form]").addEventListener("submit", (event) => handleUpdate(event, secret.id));
    elements.detailPanel.querySelector("[data-delete-secret]").addEventListener("click", () => handleDelete(secret.id));
    elements.detailPanel.querySelector("[data-acl-form]").addEventListener("submit", (event) => handleAclGrant(event, secret.id));
    elements.detailPanel.querySelector("[data-acl-check]").addEventListener("click", () => handleAclCheck(secret.id));
    elements.detailPanel.querySelector("[data-acl-revoke]").addEventListener("click", () => handleAclRevoke(secret.id));
  }

  async function handleCreate(event) {
    event.preventDefault();
    const draft = readForm(event.currentTarget);
    const validation = validateSecretDraft(draft, { requirePayload: true });
    if (!validation.valid) {
      elements.createError.textContent = Object.values(validation.errors)[0];
      return;
    }

    try {
      await store.create(toCreateSecretPayload(draft));
      event.currentTarget.reset();
      elements.createError.textContent = "";
      showToast(elements.toast, "Secret created.");
    } catch (error) {
      elements.createError.textContent = error.message;
    }
  }

  async function handleUpdate(event, id) {
    event.preventDefault();
    const draft = readForm(event.currentTarget);
    const validation = validateSecretDraft(draft);
    const error = event.currentTarget.querySelector("[data-update-error]");
    if (!validation.valid) {
      error.textContent = Object.values(validation.errors)[0];
      return;
    }

    try {
      await store.update(id, toUpdateSecretPayload(draft));
      error.textContent = "";
      showToast(elements.toast, "Secret updated.");
    } catch (updateError) {
      error.textContent = updateError.message;
    }
  }

  async function handleDelete(id) {
    try {
      await store.remove(id);
      showToast(elements.toast, "Secret deleted.");
    } catch (error) {
      showToast(elements.toast, error.message);
    }
  }

  async function handleAclCheck(secretId) {
    if (aclState.pendingAction) {
      return;
    }
    const account = readAclAccount();
    const validation = validateAclAccount(account);
    if (!validation.valid) {
      aclState = { ...aclState, account, error: validation.error, feedback: "", result: null };
      renderDetail();
      return;
    }

    aclState = { ...aclState, account, error: "", feedback: "", pendingAction: "check" };
    renderDetail();
    try {
      const access = await store.checkAccess(secretId, account);
      aclState = { ...aclState, pendingAction: "", result: access };
      renderDetail();
    } catch (error) {
      aclState = { ...aclState, pendingAction: "", error: error.message, feedback: "", result: null };
      renderDetail();
    }
  }

  async function handleAclGrant(event, secretId) {
    event.preventDefault();
    if (aclState.pendingAction) {
      return;
    }

    const account = readAclAccount();
    const canRead = readAclCheckbox("[data-acl-read]");
    const canWrite = readAclCheckbox("[data-acl-write]");
    const accountValidation = validateAclAccount(account);
    if (!accountValidation.valid) {
      aclState = { ...aclState, account, canRead, canWrite, error: accountValidation.error, feedback: "", result: null };
      renderDetail();
      return;
    }

    const permissionsValidation = validateAclPermissions({ canRead, canWrite });
    if (!permissionsValidation.valid) {
      aclState = { ...aclState, account, canRead, canWrite, error: permissionsValidation.error, feedback: "", result: null };
      renderDetail();
      return;
    }

    aclState = { ...aclState, account, canRead, canWrite, error: "", feedback: "", pendingAction: "grant" };
    renderDetail();
    try {
      const response = await store.grantAccess(secretId, account, { canRead, canWrite });
      aclState = {
        ...aclState,
        pendingAction: "",
        feedback: `Grant transaction submitted: ${response.transactionHash}`
      };
      showToast(elements.toast, "Grant transaction submitted.");
      renderDetail();
    } catch (error) {
      aclState = { ...aclState, pendingAction: "", error: error.message, feedback: "", result: null };
      renderDetail();
    }
  }

  async function handleAclRevoke(secretId) {
    if (aclState.pendingAction) {
      return;
    }

    const account = readAclAccount();
    const validation = validateAclAccount(account);
    if (!validation.valid) {
      aclState = { ...aclState, account, error: validation.error, feedback: "", result: null };
      renderDetail();
      return;
    }

    aclState = { ...aclState, account, error: "", feedback: "", pendingAction: "revoke" };
    renderDetail();
    try {
      const response = await store.revokeAccess(secretId, account);
      aclState = {
        ...aclState,
        pendingAction: "",
        canRead: false,
        canWrite: false,
        result: null,
        feedback: `Revoke transaction submitted: ${response.transactionHash}`
      };
      showToast(elements.toast, "Revoke transaction submitted.");
      renderDetail();
    } catch (error) {
      aclState = { ...aclState, pendingAction: "", error: error.message, feedback: "", result: null };
      renderDetail();
    }
  }

  function readAclAccount() {
    const input = elements.detailPanel.querySelector("[data-acl-account]");
    return typeof input?.value === "string" ? input.value.trim() : "";
  }

  function readAclCheckbox(selector) {
    return Boolean(elements.detailPanel.querySelector(selector)?.checked);
  }

  function renderEmptyDetail() {
    aclState = createAclState();
    elements.detailPanel.innerHTML = `
      <div class="empty-state">
        <img src="./src/assets/vault-mark.svg" width="88" height="88" alt="">
        <h2>Select a secret</h2>
        <p>Choose an existing item to inspect metadata, update fields, or rotate its stored payload.</p>
      </div>
    `;
  }

  store.subscribe((state) => {
    currentState = state;
    render();
  });

  elements.refresh.addEventListener("click", () => store.load());
  elements.search.addEventListener("input", renderList);
  elements.createForm.addEventListener("submit", handleCreate);

  store.load();

  return {
    elements,
    render,
    renderList,
    renderDetail,
    handleCreate,
    handleUpdate,
    handleDelete,
    handleAclCheck,
    handleAclGrant,
    handleAclRevoke
  };
}

function createAclState(secretId = null) {
  return {
    secretId,
    account: "",
    canRead: true,
    canWrite: false,
    pendingAction: "",
    result: null,
    error: "",
    feedback: ""
  };
}

function ensureAclStateForSecret(state, secretId) {
  if (state.secretId === secretId) {
    return state;
  }
  return createAclState(secretId);
}

function buildDetailMarkup(secret, aclState) {
  const actionPending = Boolean(aclState.pendingAction);
  const actionLabels = getAclActionLabels(aclState.pendingAction);
  const disabledAttribute = actionPending ? "disabled" : "";
  const aclResultMarkup = buildAclResultMarkup(aclState.result);
  const readChecked = aclState.canRead ? "checked" : "";
  const writeChecked = aclState.canWrite ? "checked" : "";

  return `
    <div class="section-heading">
      <div>
        <p class="eyebrow">Secret</p>
        <h2>${escapeHtml(secret.name)}</h2>
      </div>
      <button class="danger-button" data-delete-secret type="button">Delete</button>
    </div>
    <dl class="metadata-grid">
      <div><dt>ID</dt><dd>${escapeHtml(secret.id)}</dd></div>
      <div><dt>Created</dt><dd>${formatTimestamp(secret.createdAt)}</dd></div>
      <div><dt>Updated</dt><dd>${formatTimestamp(secret.updatedAt)}</dd></div>
    </dl>
    <form data-update-form class="secret-form">
      <label>
        Name
        <input name="name" maxlength="128" required value="${escapeAttribute(secret.name)}">
      </label>
      <label>
        Description
        <textarea name="description" maxlength="512" rows="3">${escapeHtml(secret.description ?? "")}</textarea>
      </label>
      <label>
        Rotate payload
        <textarea name="payload" maxlength="8192" rows="5" placeholder="Leave blank to keep the current payload"></textarea>
      </label>
      <label>
        Tags
        <input name="tags" value="${escapeAttribute((secret.tags ?? []).join(", "))}">
      </label>
      <p class="form-error" data-update-error role="alert"></p>
      <button class="primary-button" type="submit">Save changes</button>
    </form>
    <section class="acl-panel" aria-label="Access control management">
      <div class="section-heading">
        <div>
          <p class="eyebrow">ACL</p>
          <h3>Access management</h3>
        </div>
      </div>
      <form class="secret-form" data-acl-form>
        <label>
          Account
          <input data-acl-account name="account" autocomplete="off" placeholder="0x1111111111111111111111111111111111111111" value="${escapeAttribute(aclState.account)}">
        </label>
        <div class="permission-grid">
          <label class="checkbox-label">
            <input data-acl-read type="checkbox" ${readChecked}>
            Can read
          </label>
          <label class="checkbox-label">
            <input data-acl-write type="checkbox" ${writeChecked}>
            Can write
          </label>
        </div>
        <p class="hint-text">Grant/Revoke returns a transaction hash because blockchain updates are asynchronous.</p>
        <p class="form-error" data-acl-error role="alert">${escapeHtml(aclState.error)}</p>
        <div class="acl-actions">
          <button class="primary-button" data-acl-check type="button" ${disabledAttribute}>${actionLabels.check}</button>
          <button class="primary-button" data-acl-grant type="submit" ${disabledAttribute}>${actionLabels.grant}</button>
          <button class="danger-button" data-acl-revoke type="button" ${disabledAttribute}>${actionLabels.revoke}</button>
        </div>
        ${aclResultMarkup}
        <p class="acl-feedback" data-acl-feedback>${escapeHtml(aclState.feedback)}</p>
      </form>
    </section>
  `;
}

function getAclActionLabels(pendingAction) {
  const labels = {
    check: "Check access",
    grant: "Grant access",
    revoke: "Revoke access"
  };

  if (pendingAction === "check") {
    labels.check = "Checking...";
  } else if (pendingAction === "grant") {
    labels.grant = "Granting...";
  } else if (pendingAction === "revoke") {
    labels.revoke = "Revoking...";
  }

  return labels;
}

function buildAclResultMarkup(result) {
  if (!result) {
    return `<p class="acl-result neutral">No access check performed yet.</p>`;
  }

  const hasAccess = result.canRead || result.canWrite;
  const className = hasAccess ? "allowed" : "denied";
  const readLabel = result.canRead ? "Yes" : "No";
  const writeLabel = result.canWrite ? "Yes" : "No";

  return `<p class="acl-result ${className}">
    Read: <strong>${readLabel}</strong> ·
    Write: <strong>${writeLabel}</strong>
  </p>`;
}

export function readForm(form) {
  const data = new FormData(form);
  return {
    name: readTextField(data, "name"),
    description: readTextField(data, "description"),
    payload: readTextField(data, "payload"),
    tags: parseTags(readTextField(data, "tags"))
  };
}

export function showToast(toastElement, message) {
  toastElement.textContent = message;
  toastElement.classList.add("visible");
  globalThis.clearTimeout(showToast.timeoutId);
  showToast.timeoutId = globalThis.setTimeout(() => {
    toastElement.classList.remove("visible");
  }, 2400);
}

function readTextField(data, fieldName) {
  const value = data.get(fieldName);
  return typeof value === "string" ? value : "";
}

export function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

export function escapeAttribute(value) {
  return escapeHtml(value).replaceAll("`", "&#096;");
}

if (globalThis.document !== undefined) {
  createApp();
}
