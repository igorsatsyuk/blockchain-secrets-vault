# Secrets API

Issue #7 introduces the MVP CRUD contract for off-chain secret metadata and
payload handling. Encryption, KMS key lifecycle and blockchain ACL checks are
owned by follow-up issues #8 and #9.

Base path: `/api/v1/secrets`

## Model

Responses intentionally do not return the stored payload.

```json
{
  "id": "21f51f8a-e0a4-457b-93ae-6ba78d8be5cc",
  "name": "payment-api",
  "description": "API token",
  "tags": ["prod", "payments"],
  "createdAt": "2026-06-01T12:00:00Z",
  "updatedAt": "2026-06-01T12:00:00Z"
}
```

## Create Secret

`POST /api/v1/secrets`

```json
{
  "name": "payment-api",
  "description": "API token",
  "payload": "secret-value",
  "tags": ["prod", "payments"]
}
```

Returns `201 Created` with `Location: /api/v1/secrets/{id}` and the created
secret summary in the response body. The response body uses the Model shape
above and intentionally omits `payload`.

## List Secrets

`GET /api/v1/secrets`

Returns `200 OK` with an array of secret summaries ordered by creation time.

## Get Secret

`GET /api/v1/secrets/{id}`

Returns `200 OK` when found or `404 Not Found` when the id is unknown.

## Update Secret

`PUT /api/v1/secrets/{id}`

All fields are optional, but at least one field must be provided.

```json
{
  "name": "payment-api-renamed",
  "payload": "rotated-value"
}
```

Returns `200 OK` with the updated secret summary.

## Delete Secret

`DELETE /api/v1/secrets/{id}`

Returns `204 No Content` when deleted or `404 Not Found` when the id is unknown.

## Errors

Validation failures return `400 Bad Request`. Duplicate names return
`409 Conflict`.

```json
{
  "timestamp": "2026-06-01T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Request validation failed",
  "details": {
    "name": "must not be blank"
  }
}
```
