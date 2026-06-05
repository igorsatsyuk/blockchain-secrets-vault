const DEFAULT_BASE_URL = "/api/v1/secrets";

export function parseTags(value) {
  if (Array.isArray(value)) {
    return dedupeTags(value);
  }
  return dedupeTags(String(value ?? "").split(","));
}

export function formatTimestamp(value) {
  if (!value) {
    return "Not recorded";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "Invalid date";
  }
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(date);
}

export function validateSecretDraft(draft, options = {}) {
  const errors = {};
  const requirePayload = options.requirePayload ?? false;
  const name = draft.name?.trim() ?? "";
  const payload = draft.payload?.trim() ?? "";
  const description = draft.description?.trim() ?? "";

  if (!name && options.requireName !== false) {
    errors.name = "Name is required.";
  } else if (name.length > 128) {
    errors.name = "Name must be 128 characters or fewer.";
  }

  if ((requirePayload || payload.length > 0) && !payload) {
    errors.payload = "Payload is required.";
  } else if (payload.length > 8192) {
    errors.payload = "Payload must be 8192 characters or fewer.";
  }

  if (description.length > 512) {
    errors.description = "Description must be 512 characters or fewer.";
  }

  const oversizedTag = parseTags(draft.tags).find((tag) => tag.length > 64);
  if (oversizedTag) {
    errors.tags = "Tags must be 64 characters or fewer.";
  }

  return {
    valid: Object.keys(errors).length === 0,
    errors
  };
}

export function validateAclAccount(account) {
  const normalized = String(account ?? "").trim();
  if (!/^0x[a-fA-F0-9]{40}$/.test(normalized)) {
    return {
      valid: false,
      error: "Account must be a valid Ethereum address."
    };
  }
  return { valid: true, error: "" };
}

export function validateAclPermissions(permissions) {
  if (permissions?.canRead || permissions?.canWrite) {
    return { valid: true, error: "" };
  }
  return {
    valid: false,
    error: "Select at least one permission."
  };
}

export function normalizeAuditEvents(events) {
  if (!Array.isArray(events)) {
    return [];
  }

  return events.map((event, index) => ({
    id: String(event.id ?? event.transactionHash ?? event.detailsHash ?? `audit-${index}`),
    secretId: String(event.secretId ?? ""),
    account: String(event.account ?? "").trim(),
    action: normalizeAuditAction(event.action),
    occurredAt: event.occurredAt ?? event.timestamp ?? event.createdAt ?? null,
    transactionHash: event.transactionHash ?? "",
    detailsHash: event.detailsHash ?? "",
    status: event.status ?? ""
  }));
}

export function filterAuditEvents(events, filters = {}) {
  const action = normalizeAuditAction(filters.action);
  const account = String(filters.account ?? "").trim().toLowerCase();

  return normalizeAuditEvents(events).filter((event) => {
    if (action && event.action !== action) {
      return false;
    }
    if (account && !event.account.toLowerCase().includes(account)) {
      return false;
    }
    return true;
  });
}

export function toCreateSecretPayload(formData) {
  return {
    name: formData.name.trim(),
    description: emptyToNull(formData.description),
    payload: formData.payload.trim(),
    tags: parseTags(formData.tags)
  };
}

export function toUpdateSecretPayload(formData) {
  const payload = {};
  if (formData.name?.trim()) {
    payload.name = formData.name.trim();
  }
  if (formData.description !== undefined) {
    payload.description = emptyToNull(formData.description);
  }
  if (formData.payload?.trim()) {
    payload.payload = formData.payload.trim();
  }
  if (formData.tags !== undefined) {
    payload.tags = parseTags(formData.tags);
  }
  return payload;
}

export function createSecretsApi(options = {}) {
  const baseUrl = options.baseUrl ?? DEFAULT_BASE_URL;
  const fetchImpl = Object.hasOwn(options, "fetchImpl") ? options.fetchImpl : globalThis.fetch;
  if (typeof fetchImpl !== "function") {
    throw new TypeError("A fetch implementation is required.");
  }

  return {
    list: () => requestJson(fetchImpl, baseUrl),
    get: (id) => requestJson(fetchImpl, `${baseUrl}/${encodeURIComponent(id)}`),
    create: (secret) => requestJson(fetchImpl, baseUrl, {
      method: "POST",
      body: JSON.stringify(secret)
    }),
    update: (id, secret) => requestJson(fetchImpl, `${baseUrl}/${encodeURIComponent(id)}`, {
      method: "PUT",
      body: JSON.stringify(secret)
    }),
    remove: (id) => requestJson(fetchImpl, `${baseUrl}/${encodeURIComponent(id)}`, {
      method: "DELETE"
    }),
    grantAccess: (id, account, permissions) => requestJson(fetchImpl, `${baseUrl}/${encodeURIComponent(id)}/acl/${encodeURIComponent(account)}`, {
      method: "PUT",
      body: JSON.stringify({
        canRead: Boolean(permissions?.canRead),
        canWrite: Boolean(permissions?.canWrite)
      })
    }),
    getAccess: (id, account) => requestJson(fetchImpl, `${baseUrl}/${encodeURIComponent(id)}/acl/${encodeURIComponent(account)}`),
    revokeAccess: (id, account) => requestJson(fetchImpl, `${baseUrl}/${encodeURIComponent(id)}/acl/${encodeURIComponent(account)}`, {
      method: "DELETE"
    }),
    listAudit: (id, filters = {}) => requestJson(fetchImpl, `${baseUrl}/${encodeURIComponent(id)}/audit${buildAuditQuery(filters)}`)
  };
}

export function createSecretsStore(api) {
  const listeners = new Set();
  const state = {
    loading: false,
    error: "",
    secrets: [],
    selectedId: null,
    audit: createAuditState()
  };

  const publish = () => {
    for (const listener of listeners) {
      listener(getState());
    }
  };

  const setState = (patch) => {
    Object.assign(state, patch);
    publish();
  };

  const actions = {
    subscribe(listener) {
      listeners.add(listener);
      listener(getState());
      return () => listeners.delete(listener);
    },
    async load() {
      setState({ loading: true, error: "" });
      try {
        const secrets = await api.list();
        setState({ secrets, loading: false, selectedId: state.selectedId ?? secrets[0]?.id ?? null });
      } catch (error) {
        setState({ loading: false, error: error.message });
      }
    },
    select(id) {
      setState({ selectedId: id });
    },
    async create(secret) {
      const created = await api.create(secret);
      setState({ secrets: [...state.secrets, created], selectedId: created.id });
      return created;
    },
    async update(id, secret) {
      const updated = await api.update(id, secret);
      setState({
        secrets: state.secrets.map((item) => item.id === id ? updated : item),
        selectedId: updated.id
      });
      return updated;
    },
    async remove(id) {
      await api.remove(id);
      const secrets = state.secrets.filter((item) => item.id !== id);
      setState({ secrets, selectedId: secrets[0]?.id ?? null });
    },
    async grantAccess(id, account, permissions) {
      return api.grantAccess(id, account, permissions);
    },
    async checkAccess(id, account) {
      return api.getAccess(id, account);
    },
    async revokeAccess(id, account) {
      return api.revokeAccess(id, account);
    },
    setAuditFilters(filters) {
      setState({
        audit: {
          ...state.audit,
          filters: normalizeAuditFilters(filters)
        }
      });
    },
    async loadAudit(id, filters = state.audit.filters) {
      const normalizedFilters = normalizeAuditFilters(filters);
      setState({
        audit: {
          ...state.audit,
          secretId: id,
          filters: normalizedFilters,
          loading: true,
          error: ""
        }
      });
      try {
        const events = await api.listAudit(id, normalizedFilters);
        if (!isCurrentAuditRequest(state.audit, id, normalizedFilters)) {
          return;
        }
        setState({
          audit: {
            secretId: id,
            filters: normalizedFilters,
            loading: false,
            error: "",
            events: filterAuditEvents(events, normalizedFilters)
          }
        });
      } catch (error) {
        if (!isCurrentAuditRequest(state.audit, id, normalizedFilters)) {
          return;
        }
        setState({
          audit: {
            ...state.audit,
            secretId: id,
            filters: normalizedFilters,
            loading: false,
            error: error.message,
            events: []
          }
        });
      }
    },
    getState
  };

  function getState() {
    return {
      ...state,
      selected: state.secrets.find((secret) => secret.id === state.selectedId) ?? null
    };
  }

  return actions;
}

async function requestJson(fetchImpl, url, options = {}) {
  const restOptions = { ...options };
  delete restOptions.headers;

  const headers = {
    Accept: "application/json",
    ...(options.body ? { "Content-Type": "application/json" } : {})
  };
  if (options.headers) {
    Object.assign(headers, options.headers);
  }

  const response = await fetchImpl(url, {
    ...restOptions,
    headers
  });

  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
  }
  if (response.status === 204) {
    return null;
  }
  return response.json();
}

async function readErrorMessage(response) {
  try {
    const body = await response.json();
    if (body?.details && typeof body.details === "object") {
      return Object.values(body.details).join(" ");
    }
    return body?.message || `Request failed with status ${response.status}.`;
  } catch {
    return `Request failed with status ${response.status}.`;
  }
}

function dedupeTags(tags) {
  return [...new Set(tags.map((tag) => String(tag).trim()).filter(Boolean))];
}

function emptyToNull(value) {
  const trimmed = value?.trim() ?? "";
  return trimmed.length ? trimmed : null;
}

function createAuditState() {
  return {
    secretId: null,
    loading: false,
    error: "",
    events: [],
    filters: {
      action: "",
      account: ""
    }
  };
}

export function normalizeAuditFilters(filters = {}) {
  return {
    action: normalizeAuditAction(filters.action),
    account: String(filters.account ?? "").trim()
  };
}

function normalizeAuditAction(action) {
  const normalized = String(action ?? "").trim().toUpperCase();
  return ["REGISTER", "READ", "WRITE", "GRANT", "REVOKE"].includes(normalized) ? normalized : "";
}

function isCurrentAuditRequest(auditState, secretId, filters) {
  return auditState.secretId === secretId
    && auditState.filters.action === filters.action
    && auditState.filters.account === filters.account;
}

function buildAuditQuery(filters = {}) {
  const normalizedFilters = normalizeAuditFilters(filters);
  const params = new URLSearchParams();
  if (normalizedFilters.action) {
    params.set("action", normalizedFilters.action);
  }
  if (normalizedFilters.account) {
    params.set("account", normalizedFilters.account);
  }
  const query = params.toString();
  return query ? `?${query}` : "";
}
