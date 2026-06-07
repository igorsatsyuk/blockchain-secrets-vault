import {
  createAuthApi,
  createSecretsApi,
  createSecretsStore,
  createTokenStorage,
  formatTimestamp,
  normalizeAuditFilters,
  parseTags,
  toCreateSecretPayload,
  toUpdateSecretPayload,
  validateAclAccount,
  validateAclPermissions,
  validateSecretDraft
} from "./core.js";

export function createApp(options = {}) {
  const documentRef = options.document ?? globalThis.document;
  const tokenStorage = options.tokenStorage ?? createTokenStorage();
  const authApi = options.authApi ?? createAuthApi();
  const getAuthToken = () => tokenStorage.get();
  const store = options.store ?? createSecretsStore(createSecretsApi({ getToken: getAuthToken }));

  const elements = {
    authPanel: documentRef.querySelector("[data-auth-panel]"),
    authForm: documentRef.querySelector("[data-auth-form]"),
    authError: documentRef.querySelector("[data-auth-error]"),
    authUser: documentRef.querySelector("[data-auth-user]"),
    logout: documentRef.querySelector("[data-logout]"),
    appShell: documentRef.querySelector("[data-app-shell]"),
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
  let lastAuditRequestKey = "";
  let authenticated = Boolean(tokenStorage.get());

  function render() {
    renderAuth();
    elements.listStatus.textContent = currentState.loading ? "Loading secrets..." : currentState.error;
    renderList();
    renderDetail();
  }

  function renderAuth() {
    if (elements.authPanel) {
      elements.authPanel.hidden = authenticated;
    }
    if (elements.appShell) {
      elements.appShell.hidden = !authenticated;
    }
    if (elements.authUser) {
      elements.authUser.textContent = authenticated ? "Authenticated" : "";
    }
  }

  async function handleLogin(event) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const username = readTextField(data, "username").trim();
    const password = readTextField(data, "password");
    if (!username || !password.trim()) {
      setAuthError("Username and password are required.");
      return;
    }

    try {
      const response = await authApi.login({ username, password });
      tokenStorage.set(response.accessToken);
      authenticated = true;
      setAuthError("");
      event.currentTarget.reset();
      renderAuth();
      await store.load();
    } catch (error) {
      setAuthError(error.message);
    }
  }

  function setAuthError(message) {
    if (elements.authError) {
      elements.authError.textContent = message;
    }
  }

  function handleLogout() {
    tokenStorage.clear();
    authenticated = false;
    lastAuditRequestKey = "";
    store.clear?.();
    renderAuth();
    showOptionalToast("Signed out.");
  }

  function showOptionalToast(message) {
    if (elements.toast) {
      showToast(elements.toast, message);
    }
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
    const auditState = currentState.audit?.secretId === secret.id ? currentState.audit : createAuditViewState(secret.id);
    elements.detailPanel.innerHTML = buildDetailMarkup(secret, aclState, auditState);

    elements.detailPanel.querySelector("[data-update-form]").addEventListener("submit", (event) => handleUpdate(event, secret.id));
    elements.detailPanel.querySelector("[data-delete-secret]").addEventListener("click", () => handleDelete(secret.id));
    elements.detailPanel.querySelector("[data-acl-form]").addEventListener("submit", handleAclFormSubmit);
    elements.detailPanel.querySelector("[data-acl-check]").addEventListener("click", () => handleAclCheck(secret.id));
    elements.detailPanel.querySelector("[data-acl-grant]").addEventListener("click", (event) => handleAclGrant(event, secret.id));
    elements.detailPanel.querySelector("[data-acl-revoke]").addEventListener("click", () => handleAclRevoke(secret.id));
    elements.detailPanel.querySelector("[data-audit-form]").addEventListener("submit", (event) => handleAuditFilter(event, secret.id));
    elements.detailPanel.querySelector("[data-audit-refresh]").addEventListener("click", () => requestAudit(secret.id, { force: true }));
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
      if (aclState.secretId !== secretId) {
        return;
      }
      aclState = { ...aclState, pendingAction: "", result: access };
      renderDetail();
    } catch (error) {
      if (aclState.secretId !== secretId) {
        return;
      }
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
      if (aclState.secretId !== secretId) {
        return;
      }
      aclState = {
        ...aclState,
        pendingAction: "",
        result: null,
        feedback: `Grant transaction submitted: ${response.transactionHash}`
      };
      showToast(elements.toast, "Grant transaction submitted.");
      renderDetail();
      requestAudit(secretId, { force: true });
    } catch (error) {
      if (aclState.secretId !== secretId) {
        return;
      }
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
      if (aclState.secretId !== secretId) {
        return;
      }
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
      requestAudit(secretId, { force: true });
    } catch (error) {
      if (aclState.secretId !== secretId) {
        return;
      }
      aclState = { ...aclState, pendingAction: "", error: error.message, feedback: "", result: null };
      renderDetail();
    }
  }

  async function handleAuditFilter(event, secretId) {
    event.preventDefault();
    const filters = readAuditFilters();
    await requestAudit(secretId, { filters, force: true });
  }

  async function requestAudit(secretId, options = {}) {
    if (typeof store.loadAudit !== "function") {
      return;
    }
    const defaultFilters = currentState.audit?.secretId === secretId ? currentState.audit.filters : {};
    const filters = normalizeAuditFilters(options.filters ?? defaultFilters);
    const requestKey = `${secretId}:${filters.action}:${filters.account}`;
    if (!options.force && lastAuditRequestKey === requestKey) {
      return;
    }
    lastAuditRequestKey = requestKey;
    await store.loadAudit(secretId, filters);
  }

  function readAclAccount() {
    const input = elements.detailPanel.querySelector("[data-acl-account]");
    return typeof input?.value === "string" ? input.value.trim() : "";
  }

  function readAclCheckbox(selector) {
    return Boolean(elements.detailPanel.querySelector(selector)?.checked);
  }

  function readAuditFilters() {
    return {
      action: elements.detailPanel.querySelector("[data-audit-action]")?.value ?? "",
      account: elements.detailPanel.querySelector("[data-audit-account]")?.value ?? ""
    };
  }

  function renderEmptyDetail() {
    aclState = createAclState();
    lastAuditRequestKey = "";
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
    if (currentState.selectedId) {
      requestAudit(currentState.selectedId);
    }
  });

  elements.authForm?.addEventListener("submit", handleLogin);
  elements.logout?.addEventListener("click", handleLogout);
  elements.refresh.addEventListener("click", () => store.load());
  elements.search.addEventListener("input", renderList);
  elements.createForm.addEventListener("submit", handleCreate);

  renderAuth();
  if (authenticated) {
    store.load();
  }

  return {
    elements,
    render,
    renderList,
    renderDetail,
    handleLogin,
    handleLogout,
    handleCreate,
    handleUpdate,
    handleDelete,
    handleAclCheck,
    handleAclGrant,
    handleAclRevoke,
    handleAuditFilter
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

function handleAclFormSubmit(event) {
  event.preventDefault();
}

function createAuditViewState(secretId = null) {
  return {
    secretId,
    loading: false,
    error: "",
    events: [],
    filters: {
      action: "",
      account: ""
    }
  };
}

function buildDetailMarkup(secret, aclState, auditState) {
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
          <button class="primary-button" data-acl-grant type="button" ${disabledAttribute}>${actionLabels.grant}</button>
          <button class="danger-button" data-acl-revoke type="button" ${disabledAttribute}>${actionLabels.revoke}</button>
        </div>
        ${aclResultMarkup}
        <p class="acl-feedback" data-acl-feedback>${escapeHtml(aclState.feedback)}</p>
      </form>
    </section>
    ${buildAuditMarkup(auditState)}
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

function buildAuditMarkup(auditState) {
  const filters = auditState.filters ?? {};
  const actionOptions = ["", "REGISTER", "READ", "WRITE", "GRANT", "REVOKE"].map((action) => {
    const label = action || "All actions";
    const selected = action === (filters.action ?? "") ? "selected" : "";
    return `<option value="${action}" ${selected}>${label}</option>`;
  }).join("");
  const events = auditState.events ?? [];
  const rows = events.map(buildAuditEventMarkup).join("");
  const status = getAuditStatus(auditState, events.length);

  return `
    <section class="audit-panel" aria-label="Audit history">
      <div class="section-heading">
        <div>
          <p class="eyebrow">Audit</p>
          <h3>History</h3>
        </div>
        <button class="icon-button" data-audit-refresh type="button" title="Refresh audit history" aria-label="Refresh audit history">↻</button>
      </div>
      <form class="audit-filters" data-audit-form>
        <label>
          Action
          <select data-audit-action name="action">${actionOptions}</select>
        </label>
        <label>
          Account
          <input data-audit-account name="account" autocomplete="off" placeholder="Filter by address" value="${escapeAttribute(filters.account ?? "")}">
        </label>
        <button class="primary-button" type="submit">Apply filters</button>
      </form>
      <p class="audit-status" data-audit-status>${escapeHtml(status)}</p>
      <ol class="audit-list" data-audit-list>
        ${rows}
      </ol>
    </section>
  `;
}

function buildAuditEventMarkup(event) {
  const hash = event.transactionHash || event.detailsHash || "";
  const hashMarkup = hash ? `<span class="audit-hash">${escapeHtml(hash)}</span>` : "";
  const statusMarkup = event.status ? `<span class="audit-event-status">${escapeHtml(event.status)}</span>` : "";
  const dateTime = toDateTimeAttribute(event.occurredAt);
  const dateTimeAttribute = dateTime ? ` datetime="${escapeAttribute(dateTime)}"` : "";

  return `
    <li class="audit-event">
      <div class="audit-event-top">
        <span class="audit-action">${escapeHtml(event.action || "UNKNOWN")}</span>
        <time${dateTimeAttribute}>${formatTimestamp(event.occurredAt)}</time>
      </div>
      <div class="audit-event-account">${escapeHtml(event.account || "Account not recorded")}</div>
      ${hashMarkup}
      ${statusMarkup}
    </li>
  `;
}

function toDateTimeAttribute(value) {
  if (!value) {
    return "";
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "" : date.toISOString();
}

function getAuditStatus(auditState, eventCount) {
  if (auditState.loading) {
    return "Loading audit history...";
  }
  if (auditState.error) {
    return auditState.error;
  }
  if (eventCount === 0) {
    return "No audit events match the current filters.";
  }
  return `${eventCount} audit event${eventCount === 1 ? "" : "s"} shown.`;
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
