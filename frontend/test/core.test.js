import assert from "node:assert/strict";
import { test } from "node:test";
import {
  createSecretsApi,
  createSecretsStore,
  formatTimestamp,
  parseTags,
  toCreateSecretPayload,
  toUpdateSecretPayload,
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
    if (options.method === "DELETE") {
      return response(null, { status: 204 });
    }
    return response(secretA);
  };
  const api = createSecretsApi({ baseUrl: "/secrets", fetchImpl });

  assert.deepEqual(await api.list(), [secretA]);
  await api.get("abc/123");
  await api.create({ name: "x" });
  await api.update("id-1", { name: "y" });
  assert.equal(await api.remove("id-1"), null);

  assert.equal(calls[1].url, "/secrets/abc%2F123");
  assert.equal(calls[2].options.method, "POST");
  assert.equal(calls[2].options.headers["Content-Type"], "application/json");
  assert.equal(calls[3].options.method, "PUT");
  assert.equal(calls[4].options.method, "DELETE");
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
    remove: async () => null
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
  assert.ok(events.length >= 7);
  unsubscribe();

  const failingStore = createSecretsStore({ ...api, list: async () => { throw new Error("offline"); } });
  await failingStore.load();
  assert.equal(failingStore.getState().error, "offline");
});

function response(body, options = {}) {
  return {
    ok: options.ok ?? true,
    status: options.status ?? 200,
    json: async () => body
  };
}
