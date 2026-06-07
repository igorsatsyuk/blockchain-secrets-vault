import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { createApp, escapeAttribute, escapeHtml, readForm, showToast } from "../src/app.js";

test("login form posts credentials when JavaScript is unavailable", () => {
  const html = readFileSync(new URL("../index.html", import.meta.url), "utf8");
  assert.match(html, /<form[^>]*data-auth-form[^>]*method="post"/);
});

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
  const aclGrantButton = createElement();
  const aclRevokeButton = createElement();
  const auditForm = createElement();
  const auditAction = createElement();
  auditAction.value = "GRANT";
  const auditAccount = createElement();
  auditAccount.value = "0x1111111111111111111111111111111111111111";
  const auditRefreshButton = createElement();
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
    if (selector === "[data-acl-grant]") {
      return aclGrantButton;
    }
    if (selector === "[data-acl-revoke]") {
      return aclRevokeButton;
    }
    if (selector === "[data-audit-form]") {
      return auditForm;
    }
    if (selector === "[data-audit-action]") {
      return auditAction;
    }
    if (selector === "[data-audit-account]") {
      return auditAccount;
    }
    if (selector === "[data-audit-refresh]") {
      return auditRefreshButton;
    }
    return createElement();
  };

  const createForm = createElement();
  createForm.reset = () => {
    createForm.values = { name: "", description: "", payload: "", tags: "" };
  };
  createForm.values = { name: "alpha", description: "desc", payload: "payload", tags: "prod" };

  const elements = {
    "[data-auth-panel]": createElement(),
    "[data-auth-form]": createElement(),
    "[data-auth-error]": createElement(),
    "[data-auth-user]": createElement(),
    "[data-logout]": createElement(),
    "[data-app-shell]": createElement(),
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
  const otherSecret = {
    ...secret,
    id: "secret-race",
    name: "beta"
  };

  let state = {
    loading: false,
    error: "",
    secrets: [],
    selectedId: null,
    selected: null,
    audit: {
      secretId: null,
      loading: false,
      error: "",
      events: [],
      filters: {
        action: "",
        account: ""
      }
    }
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
    auditLoads: 0,
    clears: 0,
    auditRequests: [],
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
    clear() {
      this.clears += 1;
      state = {
        ...state,
        secrets: [],
        selectedId: null,
        selected: null,
        audit: {
          secretId: null,
          loading: false,
          error: "",
          events: [],
          filters: {
            action: "",
            account: ""
          }
        }
      };
      subscriber(state);
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
    },
    async loadAudit(id, filters = state.audit.filters) {
      this.auditLoads += 1;
      this.auditRequests.push({ id, filters });
      state = {
        ...state,
        audit: {
          secretId: id,
          loading: false,
          error: "",
          filters,
          events: [
            {
              id: "audit-1",
              action: filters.action || "GRANT",
              account: filters.account || "0x1111111111111111111111111111111111111111",
              occurredAt: "2026-06-03T12:00:00Z",
              transactionHash: "0xaudit",
              status: "accepted"
            }
          ]
        }
      };
      subscriber(state);
    }
  };
  const tokenStorage = {
    token: "jwt-token",
    get() {
      return this.token;
    },
    set(token) {
      this.token = token;
    },
    clear() {
      this.token = "";
    }
  };
  const authApi = {
    logins: 0,
    async login(credentials) {
      this.logins += 1;
      if (credentials.password === "bad") {
        throw new Error("login failed");
      }
      return { accessToken: "new-jwt-token" };
    }
  };

  try {
    const app = createApp({ document: documentRef, store, tokenStorage, authApi });
    assert.equal(store.loads, 1);
    assert.equal(elements["[data-app-shell]"].hidden, false);
    assert.equal(elements["[data-auth-panel]"].hidden, true);
    assert.match(elements["[data-list-status]"].textContent, /No secrets yet\./);
    assert.match(detailPanel.innerHTML, /Select a secret/);

    state = { ...state, secrets: [secret], selectedId: secret.id, selected: secret };
    subscriber(state);
    assert.match(detailPanel.innerHTML, /Save changes/);
    assert.match(detailPanel.innerHTML, /data-acl-grant type="button"/);
    await Promise.resolve();
    assert.equal(store.auditLoads, 1);
    assert.match(detailPanel.innerHTML, /0xaudit/);
    assert.match(detailPanel.innerHTML, /<time datetime="2026-06-03T12:00:00\.000Z">/);

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

    const authForm = elements["[data-auth-form]"];
    authForm.reset = () => {};
    authForm.values = { username: "admin", password: "secret" };
    await app.handleLogin({
      preventDefault() {},
      currentTarget: authForm
    });
    assert.equal(authApi.logins, 1);
    assert.equal(tokenStorage.token, "new-jwt-token");
    assert.equal(elements["[data-auth-error]"].textContent, "");

    authForm.values = { username: "", password: "" };
    await app.handleLogin({
      preventDefault() {},
      currentTarget: authForm
    });
    assert.match(elements["[data-auth-error]"].textContent, /required/i);
    assert.equal(authApi.logins, 1);

    authForm.values = { username: "   ", password: "secret" };
    await app.handleLogin({
      preventDefault() {},
      currentTarget: authForm
    });
    assert.match(elements["[data-auth-error]"].textContent, /required/i);
    assert.equal(authApi.logins, 1);

    authForm.values = { username: "admin", password: "   " };
    await app.handleLogin({
      preventDefault() {},
      currentTarget: authForm
    });
    assert.match(elements["[data-auth-error]"].textContent, /required/i);
    assert.equal(authApi.logins, 1);

    authForm.values = { username: "admin", password: "bad" };
    await app.handleLogin({
      preventDefault() {},
      currentTarget: authForm
    });
    assert.equal(elements["[data-auth-error]"].textContent, "login failed");

    app.handleLogout();
    assert.equal(tokenStorage.token, "");
    assert.equal(store.clears, 1);
    assert.equal(state.secrets.length, 0);
    assert.equal(elements["[data-app-shell]"].hidden, true);

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

    let aclSubmitPrevented = false;
    aclForm.trigger("submit", {
      preventDefault() {
        aclSubmitPrevented = true;
      }
    });
    assert.equal(aclSubmitPrevented, true);
    assert.equal(store.grants, 0);

    await app.handleAclCheck(secret.id);
    assert.equal(store.checks, 1);
    assert.match(detailPanel.innerHTML, /Read: <strong>Yes<\/strong>/);

    const auditLoadsBeforeGrant = store.auditLoads;
    await app.handleAclGrant(
      {
        preventDefault() {},
        currentTarget: aclForm
      },
      secret.id
    );
    assert.equal(store.grants, 1);
    assert.match(detailPanel.innerHTML, /Grant transaction submitted: 0xgrant/);
    assert.match(detailPanel.innerHTML, /No access check performed yet\./);
    await Promise.resolve();
    assert.equal(store.auditLoads, auditLoadsBeforeGrant + 1);

    const auditLoadsBeforeRevoke = store.auditLoads;
    await app.handleAclRevoke(secret.id);
    assert.equal(store.revokes, 1);
    await Promise.resolve();
    assert.equal(store.auditLoads, auditLoadsBeforeRevoke + 1);

    let auditSubmitPrevented = false;
    const auditLoadsBeforeFilter = store.auditLoads;
    await app.handleAuditFilter(
      {
        preventDefault() {
          auditSubmitPrevented = true;
        }
      },
      secret.id
    );
    assert.equal(auditSubmitPrevented, true);
    assert.equal(store.auditLoads, auditLoadsBeforeFilter + 1);
    assert.match(detailPanel.innerHTML, /GRANT/);

    const switchToSecret = (selected) => {
      state = { ...state, secrets: [secret, otherSecret], selectedId: selected.id, selected };
      subscriber(state);
    };

    state = {
      ...state,
      audit: {
        ...state.audit,
        secretId: secret.id,
        filters: {
          action: "GRANT",
          account: "0x1111111111111111111111111111111111111111"
        }
      }
    };
    switchToSecret(otherSecret);
    await Promise.resolve();
    assert.deepEqual(store.auditRequests.at(-1), {
      id: otherSecret.id,
      filters: {
        action: "",
        account: ""
      }
    });

    switchToSecret(secret);
    let deferred = createDeferred();
    store.checkAccess = async () => deferred.promise;
    const staleCheck = app.handleAclCheck(secret.id);
    switchToSecret(otherSecret);
    deferred.resolve({ canRead: true, canWrite: true });
    await staleCheck;
    assert.match(detailPanel.innerHTML, /beta/);
    assert.doesNotMatch(detailPanel.innerHTML, /Read: <strong>Yes<\/strong>/);

    switchToSecret(secret);
    deferred = createDeferred();
    store.grantAccess = async () => deferred.promise;
    const staleGrant = app.handleAclGrant(
      {
        preventDefault() {},
        currentTarget: aclForm
      },
      secret.id
    );
    switchToSecret(otherSecret);
    deferred.resolve({ transactionHash: "0xstalegrant" });
    await staleGrant;
    assert.match(detailPanel.innerHTML, /beta/);
    assert.doesNotMatch(detailPanel.innerHTML, /0xstalegrant/);

    switchToSecret(secret);
    deferred = createDeferred();
    store.revokeAccess = async () => deferred.promise;
    const staleRevoke = app.handleAclRevoke(secret.id);
    switchToSecret(otherSecret);
    deferred.resolve({ transactionHash: "0xstalerevoke" });
    await staleRevoke;
    assert.match(detailPanel.innerHTML, /beta/);
    assert.doesNotMatch(detailPanel.innerHTML, /0xstalerevoke/);

    switchToSecret(secret);
    deferred = createDeferred();
    store.checkAccess = async () => deferred.promise;
    const staleCheckError = app.handleAclCheck(secret.id);
    switchToSecret(otherSecret);
    deferred.reject(new Error("stale check failed"));
    await staleCheckError;
    assert.match(detailPanel.innerHTML, /beta/);
    assert.doesNotMatch(detailPanel.innerHTML, /stale check failed/);

    switchToSecret(secret);
    store.checkAccess = async () => {
      throw new Error("check failed");
    };
    await app.handleAclCheck(secret.id);
    assert.match(detailPanel.innerHTML, /check failed/);
    assert.match(detailPanel.innerHTML, /No access check performed yet\./);

    store.grantAccess = async () => {
      throw new Error("grant failed");
    };
    aclAccount.value = "0x1111111111111111111111111111111111111111";
    aclRead.checked = true;
    aclWrite.checked = false;
    await app.handleAclGrant(
      {
        preventDefault() {},
        currentTarget: aclForm
      },
      secret.id
    );
    assert.match(detailPanel.innerHTML, /grant failed/);
    assert.match(detailPanel.innerHTML, /No access check performed yet\./);

    store.revokeAccess = async () => {
      throw new Error("revoke failed");
    };
    await app.handleAclRevoke(secret.id);
    assert.match(detailPanel.innerHTML, /revoke failed/);
    assert.match(detailPanel.innerHTML, /No access check performed yet\./);

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
    "[data-auth-panel]": createElement(),
    "[data-auth-form]": createElement(),
    "[data-auth-error]": createElement(),
    "[data-auth-user]": createElement(),
    "[data-logout]": createElement(),
    "[data-app-shell]": createElement(),
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
  globalThis.localStorage = {
    getItem: () => "jwt-token",
    setItem() {},
    removeItem() {}
  };

  try {
    await import(`../src/app.js?bootstrap=${Date.now()}`);
    await Promise.resolve();
    await Promise.resolve();
    assert.match(elements["[data-list-status]"].textContent, /No secrets yet\./);
  } finally {
    globalThis.document = originalDocument;
    globalThis.fetch = originalFetch;
    delete globalThis.localStorage;
  }
});

test("createApp binds token getter when it creates the default API store", async () => {
  const calls = [];
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (url, options = {}) => {
    calls.push({ url, options });
    return {
      ok: true,
      status: 200,
      json: async () => []
    };
  };

  const elements = createAppElements();
  const documentRef = {
    querySelector(selector) {
      return elements[selector];
    },
    createElement: createTestElement
  };
  const tokenStorage = {
    token: "jwt-token",
    get() {
      return this.token;
    },
    set(token) {
      this.token = token;
    },
    clear() {
      this.token = "";
    }
  };

  try {
    createApp({ document: documentRef, tokenStorage });
    await Promise.resolve();
    await Promise.resolve();
    assert.equal(calls[0].options.headers.Authorization, "Bearer jwt-token");
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("handleLogin tolerates missing optional auth error element", async () => {
  const originalFormData = globalThis.FormData;
  globalThis.FormData = class {
    constructor(form) {
      this.form = form;
    }

    get(name) {
      return this.form.values[name] ?? null;
    }
  };

  const elements = createAppElements();
  elements["[data-auth-error]"] = null;
  const documentRef = {
    querySelector(selector) {
      return elements[selector];
    },
    createElement: createTestElement
  };
  const store = {
    getState: () => ({
      loading: false,
      error: "",
      secrets: [],
      selectedId: null,
      selected: null,
      audit: { secretId: null, loading: false, error: "", events: [], filters: { action: "", account: "" } }
    }),
    subscribe(listener) {
      listener(this.getState());
      return () => {};
    },
    load: async () => null
  };

  try {
    const app = createApp({
      document: documentRef,
      store,
      tokenStorage: {
        get: () => "",
        set() {},
        clear() {}
      },
      authApi: {
        login: async () => ({ accessToken: "jwt-token" })
      }
    });

    await app.handleLogin({
      preventDefault() {},
      currentTarget: {
        reset() {},
        values: { username: "", password: "" }
      }
    });
  } finally {
    globalThis.FormData = originalFormData;
  }
});

test("handleLogout clears state and tolerates missing optional toast", () => {
  const elements = createAppElements();
  elements["[data-toast]"] = null;
  const documentRef = {
    querySelector(selector) {
      return elements[selector];
    },
    createElement: createTestElement
  };
  let clears = 0;
  const store = {
    getState: () => ({
      loading: false,
      error: "",
      secrets: [{ id: "secret-1", name: "alpha" }],
      selectedId: "secret-1",
      selected: { id: "secret-1", name: "alpha" },
      audit: { secretId: "secret-1", loading: false, error: "", events: [], filters: { action: "", account: "" } }
    }),
    subscribe(listener) {
      listener(this.getState());
      return () => {};
    },
    load: async () => null,
    clear() {
      clears += 1;
    }
  };
  const tokenStorage = {
    get: () => "jwt-token",
    set() {},
    clear() {}
  };

  const app = createApp({ document: documentRef, store, tokenStorage });
  app.handleLogout();

  assert.equal(clears, 1);
  assert.equal(elements["[data-app-shell]"].hidden, true);
});

function createDeferred() {
  const deferred = {};
  deferred.promise = new Promise((resolve, reject) => {
    deferred.resolve = resolve;
    deferred.reject = reject;
  });
  return deferred;
}

function createTestElement() {
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
    replaceChildren(...children) {
      this.children = children;
    },
    append(child) {
      this.children.push(child);
    },
    querySelector() {
      return createTestElement();
    }
  };
}

function createAppElements() {
  return {
    "[data-auth-panel]": createTestElement(),
    "[data-auth-form]": createTestElement(),
    "[data-auth-error]": createTestElement(),
    "[data-auth-user]": createTestElement(),
    "[data-logout]": createTestElement(),
    "[data-app-shell]": createTestElement(),
    "[data-secret-list]": createTestElement(),
    "[data-list-status]": createTestElement(),
    "[data-detail-panel]": createTestElement(),
    "[data-create-form]": createTestElement(),
    "[data-create-error]": createTestElement(),
    "[data-refresh]": createTestElement(),
    "[data-search]": createTestElement(),
    "[data-toast]": createTestElement()
  };
}
