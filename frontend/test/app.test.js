import assert from "node:assert/strict";
import { test } from "node:test";
import { createApp, escapeAttribute, escapeHtml, readForm, showToast } from "../src/app.js";

test("readForm normalizes string-only fields", () => {
  const originalFormData = globalThis.FormData;
  globalThis.FormData = class {
    constructor(form) {
      this.form = form;
    }

    get(name) {
      return this.form.values[name] ?? null;
    }
  };

  try {
    const form = {
      values: {
        name: "api",
        description: { value: "ignored-object" },
        payload: "secret",
        tags: "prod, payments"
      }
    };
    assert.deepEqual(readForm(form), {
      name: "api",
      description: "",
      payload: "secret",
      tags: ["prod", "payments"]
    });
  } finally {
    globalThis.FormData = originalFormData;
  }
});

test("escape helpers sanitize HTML and attributes", () => {
  assert.equal(escapeHtml(`<tag attr="x">'</tag>`), "&lt;tag attr=&quot;x&quot;&gt;&#039;&lt;/tag&gt;");
  assert.equal(escapeAttribute("`value`"), "&#096;value&#096;");
});

test("showToast toggles visibility and updates message", () => {
  const originalSetTimeout = globalThis.setTimeout;
  const originalClearTimeout = globalThis.clearTimeout;
  const calls = { added: 0, removed: 0 };
  const toast = {
    textContent: "",
    classList: {
      add() {
        calls.added += 1;
      },
      remove() {
        calls.removed += 1;
      }
    }
  };

  globalThis.clearTimeout = () => {};
  globalThis.setTimeout = (handler) => {
    handler();
    return 1;
  };

  try {
    showToast(toast, "Saved");
    assert.equal(toast.textContent, "Saved");
    assert.equal(calls.added, 1);
    assert.equal(calls.removed, 1);
  } finally {
    globalThis.setTimeout = originalSetTimeout;
    globalThis.clearTimeout = originalClearTimeout;
  }
});

test("createApp handles create, update, delete and rendering flows", async () => {
  const originalFormData = globalThis.FormData;
  const originalSetTimeout = globalThis.setTimeout;
  const originalClearTimeout = globalThis.clearTimeout;

  globalThis.FormData = class {
    constructor(form) {
      this.form = form;
    }

    get(name) {
      return this.form.values[name] ?? null;
    }
  };
  globalThis.clearTimeout = () => {};
  globalThis.setTimeout = (handler) => {
    handler();
    return 1;
  };

  const createElement = () => {
    const listeners = {};
    return {
      value: "",
      textContent: "",
      innerHTML: "",
      className: "",
      type: "",
      children: [],
      classList: {
        add() {},
        remove() {}
      },
      addEventListener(event, handler) {
        listeners[event] = handler;
      },
      trigger(event, payload) {
        return listeners[event]?.(payload);
      },
      replaceChildren(...children) {
        this.children = children;
      },
      append(child) {
        this.children.push(child);
      },
      querySelector(selector) {
        if (selector === "[data-update-error]") {
          return this.updateErrorElement;
        }
        return createElement();
      }
    };
  };

  const detailUpdateForm = createElement();
  detailUpdateForm.updateErrorElement = createElement();
  const detailDeleteButton = createElement();
  const aclForm = createElement();
  const aclAccount = createElement();
  aclAccount.value = "0x1111111111111111111111111111111111111111";
  const aclRead = createElement();
  aclRead.checked = true;
  const aclWrite = createElement();
  aclWrite.checked = false;
  const aclCheckButton = createElement();
  const aclRevokeButton = createElement();
  const detailPanel = createElement();
  detailPanel.querySelector = (selector) => {
    if (selector === "[data-update-form]") {
      return detailUpdateForm;
    }
    if (selector === "[data-delete-secret]") {
      return detailDeleteButton;
    }
    if (selector === "[data-acl-form]") {
      return aclForm;
    }
    if (selector === "[data-acl-account]") {
      return aclAccount;
    }
    if (selector === "[data-acl-read]") {
      return aclRead;
    }
    if (selector === "[data-acl-write]") {
      return aclWrite;
    }
    if (selector === "[data-acl-check]") {
      return aclCheckButton;
    }
    if (selector === "[data-acl-revoke]") {
      return aclRevokeButton;
    }
    return createElement();
  };

  const createForm = createElement();
  createForm.reset = () => {
    createForm.values = { name: "", description: "", payload: "", tags: "" };
  };
  createForm.values = { name: "alpha", description: "desc", payload: "payload", tags: "prod" };

  const elements = {
    "[data-secret-list]": createElement(),
    "[data-list-status]": createElement(),
    "[data-detail-panel]": detailPanel,
    "[data-create-form]": createForm,
    "[data-create-error]": createElement(),
    "[data-refresh]": createElement(),
    "[data-search]": createElement(),
    "[data-toast]": createElement()
  };

  const documentRef = {
    querySelector(selector) {
      return elements[selector];
    },
    createElement
  };

  const secret = {
    id: "secret-1",
    name: "alpha",
    description: "desc",
    tags: ["prod"],
    createdAt: "2026-06-01T12:00:00Z",
    updatedAt: "2026-06-01T12:00:00Z"
  };

  let state = {
    loading: false,
    error: "",
    secrets: [],
    selectedId: null,
    selected: null
  };
  let subscriber = () => {};

  const store = {
    loads: 0,
    creates: 0,
    updates: 0,
    deletes: 0,
    grants: 0,
    checks: 0,
    revokes: 0,
    getState() {
      return state;
    },
    subscribe(listener) {
      subscriber = listener;
      listener(state);
      return () => {};
    },
    load() {
      this.loads += 1;
      return Promise.resolve();
    },
    select(id) {
      state = { ...state, selectedId: id, selected: state.secrets.find((item) => item.id === id) ?? null };
      subscriber(state);
    },
    async create(payload) {
      this.creates += 1;
      const created = { ...secret, ...payload, id: "secret-2", tags: payload.tags ?? [] };
      state = { ...state, secrets: [...state.secrets, created], selectedId: created.id, selected: created };
      subscriber(state);
      return created;
    },
    async update(id, payload) {
      this.updates += 1;
      const updated = { ...state.secrets.find((item) => item.id === id), ...payload, id };
      state = {
        ...state,
        secrets: state.secrets.map((item) => item.id === id ? updated : item),
        selectedId: id,
        selected: updated
      };
      subscriber(state);
      return updated;
    },
    async remove(id) {
      this.deletes += 1;
      state = { ...state, secrets: state.secrets.filter((item) => item.id !== id), selectedId: null, selected: null };
      subscriber(state);
      return null;
    },
    async grantAccess() {
      this.grants += 1;
      return { transactionHash: "0xgrant" };
    },
    async checkAccess() {
      this.checks += 1;
      return { canRead: true, canWrite: false };
    },
    async revokeAccess() {
      this.revokes += 1;
      return { transactionHash: "0xrevoke" };
    }
  };

  try {
    const app = createApp({ document: documentRef, store });
    assert.equal(store.loads, 1);
    assert.match(elements["[data-list-status]"].textContent, /No secrets yet\./);
    assert.match(detailPanel.innerHTML, /Select a secret/);

    state = { ...state, secrets: [secret], selectedId: secret.id, selected: secret };
    subscriber(state);
    assert.match(detailPanel.innerHTML, /Save changes/);

    await app.handleCreate({
      preventDefault() {},
      currentTarget: createForm
    });
    assert.equal(store.creates, 1);
    assert.equal(elements["[data-create-error]"].textContent, "");

    createForm.values = { name: "", description: "", payload: "", tags: "" };
    await app.handleCreate({
      preventDefault() {},
      currentTarget: createForm
    });
    assert.match(elements["[data-create-error]"].textContent, /required/i);

    store.create = async () => {
      throw new Error("create failed");
    };
    createForm.values = { name: "alpha", description: "", payload: "value", tags: "" };
    await app.handleCreate({
      preventDefault() {},
      currentTarget: createForm
    });
    assert.equal(elements["[data-create-error]"].textContent, "create failed");

    const updateForm = {
      values: { name: "alpha-updated", description: "updated", payload: "", tags: "prod, updated" },
      querySelector: () => detailUpdateForm.updateErrorElement
    };
    await app.handleUpdate(
      {
        preventDefault() {},
        currentTarget: updateForm
      },
      secret.id
    );
    assert.equal(store.updates, 1);
    assert.equal(detailUpdateForm.updateErrorElement.textContent, "");

    updateForm.values = { name: "", description: "", payload: "", tags: "" };
    await app.handleUpdate(
      {
        preventDefault() {},
        currentTarget: updateForm
      },
      secret.id
    );
    assert.match(detailUpdateForm.updateErrorElement.textContent, /required/i);

    store.update = async () => {
      throw new Error("update failed");
    };
    updateForm.values = { name: "valid", description: "", payload: "", tags: "" };
    await app.handleUpdate(
      {
        preventDefault() {},
        currentTarget: updateForm
      },
      secret.id
    );
    assert.equal(detailUpdateForm.updateErrorElement.textContent, "update failed");

    await app.handleDelete(secret.id);
    assert.equal(store.deletes, 1);

    store.remove = async () => {
      throw new Error("delete failed");
    };
    await app.handleDelete(secret.id);
    assert.equal(elements["[data-toast]"].textContent, "delete failed");

    state = { ...state, secrets: [secret], selectedId: secret.id, selected: secret };
    subscriber(state);

    await app.handleAclCheck(secret.id);
    assert.equal(store.checks, 1);

    await app.handleAclGrant(
      {
        preventDefault() {},
        currentTarget: aclForm
      },
      secret.id
    );
    assert.equal(store.grants, 1);

    await app.handleAclRevoke(secret.id);
    assert.equal(store.revokes, 1);

    aclAccount.value = "bad-account";
    await app.handleAclCheck(secret.id);
    assert.match(detailPanel.innerHTML, /valid Ethereum address/i);
    assert.match(detailPanel.innerHTML, /No access check performed yet\./);

    aclAccount.value = "0x1111111111111111111111111111111111111111";
    aclRead.checked = false;
    aclWrite.checked = false;
    await app.handleAclGrant(
      {
        preventDefault() {},
        currentTarget: aclForm
      },
      secret.id
    );
    assert.match(detailPanel.innerHTML, /Select at least one permission/i);
    assert.match(detailPanel.innerHTML, /No access check performed yet\./);

    aclAccount.value = "bad-account";
    await app.handleAclRevoke(secret.id);
    assert.match(detailPanel.innerHTML, /valid Ethereum address/i);
    assert.match(detailPanel.innerHTML, /No access check performed yet\./);
  } finally {
    globalThis.FormData = originalFormData;
    globalThis.setTimeout = originalSetTimeout;
    globalThis.clearTimeout = originalClearTimeout;
  }
});

test("module auto-bootstraps when document exists", async () => {
  const originalDocument = globalThis.document;
  const originalFetch = globalThis.fetch;

  const createElement = () => ({
    value: "",
    textContent: "",
    innerHTML: "",
    className: "",
    classList: { add() {}, remove() {} },
    addEventListener() {},
    replaceChildren() {},
    append() {},
    querySelector() {
      return createElement();
    }
  });

  const elements = {
    "[data-secret-list]": createElement(),
    "[data-list-status]": createElement(),
    "[data-detail-panel]": createElement(),
    "[data-create-form]": createElement(),
    "[data-create-error]": createElement(),
    "[data-refresh]": createElement(),
    "[data-search]": createElement(),
    "[data-toast]": createElement()
  };

  globalThis.document = {
    querySelector(selector) {
      return elements[selector];
    },
    createElement
  };
  globalThis.fetch = async () => ({
    ok: true,
    status: 200,
    json: async () => []
  });

  try {
    await import(`../src/app.js?bootstrap=${Date.now()}`);
    await Promise.resolve();
    await Promise.resolve();
    assert.match(elements["[data-list-status]"].textContent, /No secrets yet\./);
  } finally {
    globalThis.document = originalDocument;
    globalThis.fetch = originalFetch;
  }
});
