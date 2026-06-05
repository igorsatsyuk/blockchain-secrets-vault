import {
  createSecretsApi,
  createSecretsStore,
  formatTimestamp,
  parseTags,
  toCreateSecretPayload,
  toUpdateSecretPayload,
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
      elements.detailPanel.innerHTML = `
      <div class="empty-state">
        <img src="./src/assets/vault-mark.svg" width="88" height="88" alt="">
        <h2>Select a secret</h2>
        <p>Choose an existing item to inspect metadata, update fields, or rotate its stored payload.</p>
      </div>
    `;
      return;
    }

    elements.detailPanel.innerHTML = `
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
  `;

    elements.detailPanel.querySelector("[data-update-form]").addEventListener("submit", (event) => handleUpdate(event, secret.id));
    elements.detailPanel.querySelector("[data-delete-secret]").addEventListener("click", () => handleDelete(secret.id));
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
    handleDelete
  };
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
