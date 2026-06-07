import assert from "node:assert/strict";
import { test } from "node:test";
import {
  createAuthApi,
  createSecretsApi,
  createSecretsStore,
  createTokenStorage,
  filterAuditEvents,
  formatTimestamp,
  normalizeAuditFilters,
  normalizeAuditEvents,
  parseTags,
  toCreateSecretPayload,
  toUpdateSecretPayload,
  validateAclAccount,
  validateAclPermissions,
  validateSecretDraft
} from "../src/core.js";

const secretA = {
  id: "11111111-1111-4111-8111-111111111111",
  name: "payment-api",
  description: "Payment token",
  tags: ["prod", "payments"],
  createdAt: "2026-06-01T12:00:00Z",
  updatedAt: "2026-06-01T12:00:00Z"
};

const secretB = {
  id: "22222222-2222-4222-8222-222222222222",
  name: "crm-api",
  description: null,
  tags: [],
  createdAt: "2026-06-02T12:00:00Z",
  updatedAt: "2026-06-02T12:00:00Z"
};

const auditEvents = [
  {
    id: "event-1",
    secretId: secretA.id,
    account: " 0x1111111111111111111111111111111111111111 ",
    action: "grant",
    occurredAt: "2026-06-03T12:00:00Z",
    transactionHash: "0xgrant",
    status: "accepted"
  },
  {
    secretId: secretA.id,
    account: "0x2222222222222222222222222222222222222222",
    action: "READ",
    timestamp: "2026-06-03T12:05:00Z",
    detailsHash: "0xhash"
  }
];

test("parseTags trims, removes blanks, and deduplicates values", () => {
  assert.deepEqual(parseTags("prod, payments, prod,  ,audit"), ["prod", "payments", "audit"]);
  assert.deepEqual(parseTags([" one ", "", "one", "two"]), ["one", "two"]);
  assert.deepEqual(parseTags(null), []);
});

test("formatTimestamp handles missing, invalid, and valid timestamps", () => {
  assert.equal(formatTimestamp(null), "Not recorded");
  assert.equal(formatTimestamp("not-a-date"), "Invalid date");
  const formatted = formatTimestamp("2026-06-01T12:00:00Z");
  assert.notEqual(formatted, "Not recorded");
  assert.notEqual(formatted, "Invalid date");
  assert.match(formatted, /2026/);
});

test("validateSecretDraft enforces API limits", () => {
  assert.deepEqual(validateSecretDraft({ name: "", payload: "x" }).errors, { name: "Name is required." });
  assert.equal(validateSecretDraft({ name: "a", payload: "" }, { requirePayload: true }).errors.payload, "Payload is required.");
  assert.equal(validateSecretDraft({ name: "a".repeat(129), payload: "x" }).errors.name, "Name must be 128 characters or fewer.");
  assert.equal(validateSecretDraft({ name: "a", description: "x".repeat(513) }).errors.description, "Description must be 512 characters or fewer.");
  assert.equal(validateSecretDraft({ name: "a", description: " ".repeat(513) }).errors.description, undefined);
  assert.equal(validateSecretDraft({ name: "a", tags: ["x".repeat(65)] }).errors.tags, "Tags must be 64 characters or fewer.");
  assert.equal(validateSecretDraft({ name: "valid", payload: "secret" }, { requirePayload: true }).valid, true);
});

test("ACL validators enforce account format and permissions", () => {
  assert.equal(validateAclAccount("0x1111111111111111111111111111111111111111").valid, true);
  assert.equal(validateAclAccount("0X1111111111111111111111111111111111111111").valid, false);
  assert.equal(validateAclAccount("0x123").valid, false);
  assert.match(validateAclAccount("bad").error, /ethereum address/i);

  assert.equal(validateAclPermissions({ canRead: true, canWrite: false }).valid, true);
  assert.equal(validateAclPermissions({ canRead: false, canWrite: true }).valid, true);
  assert.equal(validateAclPermissions({ canRead: false, canWrite: false }).valid, false);
});

test("audit helpers normalize and filter history events", () => {
  assert.deepEqual(normalizeAuditFilters({ action: " grant ", account: " 0xabc " }), {
    action: "GRANT",
    account: "0xabc"
  });
  assert.deepEqual(normalizeAuditFilters({ action: " register ", account: "" }), {
    action: "REGISTER",
    account: ""
  });
  assert.deepEqual(normalizeAuditFilters({ action: "bad", account: null }), {
    action: "",
    account: ""
  });
  assert.deepEqual(normalizeAuditEvents(null), []);
  assert.deepEqual(normalizeAuditEvents([
    {
      transactionHash: "0xabc",
      action: "invalid-action",
      createdAt: "2026-06-03T12:00:00Z"
    }
  ]), [
    {
      id: "0xabc",
      secretId: "",
      account: "",
      action: "",
      occurredAt: "2026-06-03T12:00:00Z",
      transactionHash: "0xabc",
      detailsHash: "",
      status: ""
    }
  ]);

  const normalized = normalizeAuditEvents(auditEvents);
  assert.equal(normalized[0].action, "GRANT");
  assert.equal(normalized[0].account, "0x1111111111111111111111111111111111111111");
  assert.equal(normalized[1].occurredAt, "2026-06-03T12:05:00Z");

  assert.deepEqual(filterAuditEvents(auditEvents, { action: "grant" }).map((event) => event.id), ["event-1"]);
  assert.deepEqual(filterAuditEvents(auditEvents, { account: "2222" }).map((event) => event.action), ["READ"]);
  assert.equal(filterAuditEvents(auditEvents, { action: "WRITE" }).length, 0);
});

test("payload mappers normalize create and update requests", () => {
  assert.deepEqual(toCreateSecretPayload({
    name: " payment-api ",
    description: " ",
    payload: " token ",
    tags: "prod, payments, prod"
  }), {
    name: "payment-api",
    description: null,
    payload: "token",
    tags: ["prod", "payments"]
  });

  assert.deepEqual(toUpdateSecretPayload({
    name: " renamed ",
    description: "",
    payload: "",
    tags: ["prod"]
  }), {
    name: "renamed",
    description: null,
    tags: ["prod"]
  });
});

test("API client sends expected HTTP requests and decodes responses", async () => {
  const calls = [];
  const fetchImpl = async (url, options = {}) => {
    calls.push({ url, options });
    if (!options.method && url === "/secrets") {
      return response([secretA]);
    }
    if (!options.method && url === "/secrets/id-1/acl/0x1111111111111111111111111111111111111111") {
      return response({
        secretId: "id-1",
        account: "0x1111111111111111111111111111111111111111",
        canRead: true,
        canWrite: false
      });
    }
    if (options.method === "PUT" && url === "/secrets/id-1/acl/0x1111111111111111111111111111111111111111") {
      return response({
        secretId: "id-1",
        account: "0x1111111111111111111111111111111111111111",
        transactionHash: "0xabc"
      }, { status: 202 });
    }
    if (options.method === "DELETE" && url === "/secrets/id-1/acl/0x1111111111111111111111111111111111111111") {
      return response({
        secretId: "id-1",
        account: "0x1111111111111111111111111111111111111111",
        transactionHash: "0xdef"
      }, { status: 202 });
    }
    if (!options.method && url === "/secrets/id-1/audit?action=GRANT&account=0x1111111111111111111111111111111111111111") {
      return response(auditEvents);
    }
    if (options.method === "DELETE") {
      return response(null, { status: 204 });
    }
    return response(secretA);
  };
  const api = createSecretsApi({ baseUrl: "/secrets", fetchImpl, getToken: () => "jwt-token" });

  assert.deepEqual(await api.list(), [secretA]);
  await api.get("abc/123");
  await api.create({ name: "x" });
  await api.update("id-1", { name: "y" });
  assert.equal(await api.remove("id-1"), null);
  assert.equal((await api.grantAccess("id-1", "0x1111111111111111111111111111111111111111", { canRead: true, canWrite: false })).transactionHash, "0xabc");
  assert.equal((await api.getAccess("id-1", "0x1111111111111111111111111111111111111111")).canRead, true);
  assert.equal((await api.revokeAccess("id-1", "0x1111111111111111111111111111111111111111")).transactionHash, "0xdef");
  assert.equal((await api.listAudit("id-1", { action: "grant", account: "0x1111111111111111111111111111111111111111" }))[0].id, "event-1");

  assert.equal(calls[1].url, "/secrets/abc%2F123");
  assert.equal(calls[2].options.method, "POST");
  assert.equal(calls[2].options.headers["Content-Type"], "application/json");
  assert.equal(calls[2].options.headers.Authorization, "Bearer jwt-token");
  assert.equal(calls[3].options.method, "PUT");
  assert.equal(calls[4].options.method, "DELETE");
  assert.equal(calls[5].options.method, "PUT");
  assert.equal(calls[5].url, "/secrets/id-1/acl/0x1111111111111111111111111111111111111111");
  assert.equal(calls[6].url, "/secrets/id-1/acl/0x1111111111111111111111111111111111111111");
  assert.equal(calls[7].options.method, "DELETE");
  assert.equal(calls[8].url, "/secrets/id-1/audit?action=GRANT&account=0x1111111111111111111111111111111111111111");
});

test("auth client logs in and token storage tolerates browser storage failures", async () => {
  const calls = [];
  const authApi = createAuthApi({
    loginUrl: "/login",
    fetchImpl: async (url, options = {}) => {
      calls.push({ url, options });
      return response({ tokenType: "Bearer", accessToken: "jwt-token", expiresIn: 3600 });
    }
  });

  assert.equal((await authApi.login({ username: " admin ", password: "secret" })).accessToken, "jwt-token");
  assert.equal(calls[0].url, "/login");
  assert.equal(calls[0].options.method, "POST");
  assert.deepEqual(JSON.parse(calls[0].options.body), { username: "admin", password: "secret" });

  const values = new Map();
  const tokenStorage = createTokenStorage({
    key: "token",
    storage: {
      getItem: (key) => values.get(key) ?? null,
      setItem: (key, value) => values.set(key, value),
      removeItem: (key) => values.delete(key)
    }
  });
  tokenStorage.set("abc");
  assert.equal(tokenStorage.get(), "abc");
  tokenStorage.clear();
  assert.equal(tokenStorage.get(), "");

  const failingStorage = createTokenStorage({
    storage: {
      getItem: () => { throw new Error("blocked"); },
      setItem: () => { throw new Error("blocked"); },
      removeItem: () => { throw new Error("blocked"); }
    }
  });
  failingStorage.set("abc");
  failingStorage.clear();
  assert.equal(failingStorage.get(), "");
  assert.throws(() => createAuthApi({ fetchImpl: null }), /fetch implementation/);
});

test("token storage falls back when localStorage access is blocked", () => {
  const descriptor = Object.getOwnPropertyDescriptor(globalThis, "localStorage");
  Object.defineProperty(globalThis, "localStorage", {
    configurable: true,
    get() {
      throw new Error("blocked");
    }
  });

  try {
    const tokenStorage = createTokenStorage();
    tokenStorage.set("jwt-token");
    assert.equal(tokenStorage.get(), "jwt-token");
    tokenStorage.clear();
    assert.equal(tokenStorage.get(), "");
  } finally {
    if (descriptor) {
      Object.defineProperty(globalThis, "localStorage", descriptor);
    } else {
      delete globalThis.localStorage;
    }
  }
});

test("API client surfaces validation details and fallback errors", async () => {
  const validationApi = createSecretsApi({
    fetchImpl: async () => response({ details: { name: "must not be blank" } }, { ok: false, status: 400 })
  });
  await assert.rejects(validationApi.list(), /must not be blank/);

  const fallbackApi = createSecretsApi({
    fetchImpl: async () => ({ ok: false, status: 502, json: async () => { throw new Error("bad json"); } })
  });
  await assert.rejects(fallbackApi.list(), /502/);

  assert.throws(() => createSecretsApi({ fetchImpl: null }), /fetch implementation/);
});

test("store loads, selects, creates, updates, removes, and reports load errors", async () => {
  const events = [];
  const api = {
    list: async () => [secretA, secretB],
    create: async (secret) => ({ ...secretA, ...secret, id: "33333333-3333-4333-8333-333333333333" }),
    update: async (id, secret) => ({ ...secretB, ...secret, id }),
    remove: async () => null,
    grantAccess: async () => ({ transactionHash: "0xabc" }),
    getAccess: async () => ({ canRead: true, canWrite: false }),
    revokeAccess: async () => ({ transactionHash: "0xdef" }),
    listAudit: async () => auditEvents
  };
  const store = createSecretsStore(api);
  const unsubscribe = store.subscribe((state) => events.push(state));

  await store.load();
  assert.equal(store.getState().selectedId, secretA.id);

  store.select(secretB.id);
  assert.equal(store.getState().selected.name, secretB.name);

  const created = await store.create({ name: "new-secret", tags: [] });
  assert.equal(store.getState().selectedId, created.id);

  const updated = await store.update(secretB.id, { name: "renamed" });
  assert.equal(updated.name, "renamed");
  assert.equal(store.getState().secrets.find((item) => item.id === secretB.id).name, "renamed");

  await store.remove(created.id);
  assert.notEqual(store.getState().selectedId, created.id);
  assert.equal((await store.grantAccess(secretB.id, "0x1111111111111111111111111111111111111111", { canRead: true, canWrite: false })).transactionHash, "0xabc");
  assert.equal((await store.checkAccess(secretB.id, "0x1111111111111111111111111111111111111111")).canRead, true);
  assert.equal((await store.revokeAccess(secretB.id, "0x1111111111111111111111111111111111111111")).transactionHash, "0xdef");
  store.setAuditFilters({ action: "read", account: "2222" });
  assert.deepEqual(store.getState().audit.filters, { action: "READ", account: "2222" });
  await store.loadAudit(secretB.id);
  assert.equal(store.getState().audit.loading, false);
  assert.equal(store.getState().audit.events.length, 1);
  assert.equal(store.getState().audit.events[0].action, "READ");
  store.clear();
  assert.equal(store.getState().secrets.length, 0);
  assert.equal(store.getState().selectedId, null);
  assert.equal(store.getState().audit.events.length, 0);
  assert.ok(events.length >= 7);
  unsubscribe();

  const failingStore = createSecretsStore({ ...api, list: async () => { throw new Error("offline"); } });
  await failingStore.load();
  assert.equal(failingStore.getState().error, "offline");

  const failingAuditStore = createSecretsStore({ ...api, listAudit: async () => { throw new Error("audit offline"); } });
  await failingAuditStore.loadAudit(secretA.id, { action: "grant" });
  assert.equal(failingAuditStore.getState().audit.error, "audit offline");
  assert.equal(failingAuditStore.getState().audit.events.length, 0);
});

test("store ignores stale audit responses for older filters on the same secret", async () => {
  const grantRequest = createDeferred();
  const readRequest = createDeferred();
  const store = createSecretsStore({
    list: async () => [secretA],
    create: async () => secretA,
    update: async () => secretA,
    remove: async () => null,
    grantAccess: async () => ({ transactionHash: "0xabc" }),
    getAccess: async () => ({ canRead: true, canWrite: false }),
    revokeAccess: async () => ({ transactionHash: "0xdef" }),
    listAudit: async (_, filters) => filters.action === "GRANT" ? grantRequest.promise : readRequest.promise
  });

  const staleLoad = store.loadAudit(secretA.id, { action: "GRANT" });
  const currentLoad = store.loadAudit(secretA.id, { action: "READ" });

  readRequest.resolve([auditEvents[1]]);
  await currentLoad;
  assert.equal(store.getState().audit.filters.action, "READ");
  assert.equal(store.getState().audit.events[0].action, "READ");

  grantRequest.resolve([auditEvents[0]]);
  await staleLoad;
  assert.equal(store.getState().audit.filters.action, "READ");
  assert.equal(store.getState().audit.events[0].action, "READ");

  const staleErrorRequest = createDeferred();
  const latestRequest = createDeferred();
  let requestIndex = 0;
  const errorStore = createSecretsStore({
    list: async () => [secretA],
    create: async () => secretA,
    update: async () => secretA,
    remove: async () => null,
    grantAccess: async () => ({ transactionHash: "0xabc" }),
    getAccess: async () => ({ canRead: true, canWrite: false }),
    revokeAccess: async () => ({ transactionHash: "0xdef" }),
    listAudit: async () => {
      requestIndex += 1;
      return requestIndex === 1 ? staleErrorRequest.promise : latestRequest.promise;
    }
  });
  const staleErrorLoad = errorStore.loadAudit(secretA.id, { action: "GRANT" });
  const latestLoad = errorStore.loadAudit(secretA.id, { action: "READ" });

  latestRequest.resolve([auditEvents[1]]);
  await latestLoad;
  staleErrorRequest.reject(new Error("stale audit failed"));
  await staleErrorLoad;
  assert.equal(errorStore.getState().audit.error, "");
  assert.equal(errorStore.getState().audit.filters.action, "READ");
});

function response(body, options = {}) {
  return {
    ok: options.ok ?? true,
    status: options.status ?? 200,
    json: async () => body
  };
}

function createDeferred() {
  const deferred = {};
  deferred.promise = new Promise((resolve, reject) => {
    deferred.resolve = resolve;
    deferred.reject = reject;
  });
  return deferred;
}
