# Secrets API

Issue #7 introduces the MVP CRUD contract for off-chain secret metadata and
payload handling. Issue #8 adds KMS-backed encryption, issue #9 adds
blockchain ACL grant, revoke and access-check operations, issue #10 adds
server-side audit event hash publishing for ACL mutations, issue #13 exposes
published audit events so the UI can browse and filter audit history, and issue
#14 adds controlled encryption key rotation with secret re-encryption.

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

## Rotate Encryption Key

`POST /api/v1/secrets/encryption-key/rotate`

Rotates the active default encryption key and re-encrypts all stored secrets
that use this key id.

Returns `200 OK` with key version metadata and the number of re-encrypted
secrets.

```json
{
  "keyId": "default-secret-key",
  "previousKeyVersion": 0,
  "newKeyVersion": 1,
  "reEncryptedSecrets": 3
}
```

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

## Grant Secret Access

`PUT /api/v1/secrets/{id}/acl/{account}`

`account` must be an Ethereum address. The secret id is encoded as the
contract `bytes32` identifier by writing the UUID bytes first and zero-padding
the remaining 16 bytes.

```json
{
  "canRead": true,
  "canWrite": false
}
```

Returns `202 Accepted` with the submitted transaction hash.
After the ACL transaction is accepted by the blockchain adapter, the backend
publishes a server-generated `GRANT` audit hash through `SecretsAcl.auditEvent`.
If the audit publish attempt fails after the ACL transaction is submitted, this
endpoint still returns the ACL transaction hash so clients can correlate the
accepted operation without retrying it blindly.

```json
{
  "secretId": "21f51f8a-e0a4-457b-93ae-6ba78d8be5cc",
  "account": "0x1111111111111111111111111111111111111111",
  "transactionHash": "0xabc123"
}
```

## Check Secret Access

`GET /api/v1/secrets/{id}/acl/{account}`

Returns `200 OK` with read/write permissions resolved from the ACL contract.

```json
{
  "secretId": "21f51f8a-e0a4-457b-93ae-6ba78d8be5cc",
  "account": "0x1111111111111111111111111111111111111111",
  "canRead": true,
  "canWrite": false
}
```

## List Secret Audit History

`GET /api/v1/secrets/{id}/audit`

Optional query parameters:

- `action`: one of `REGISTER`, `READ`, `WRITE`, `GRANT`, `REVOKE`
- `account`: substring filter for the blockchain account address

Returns `200 OK` with audit events published for the secret.

```json
[
  {
    "secretId": "21f51f8a-e0a4-457b-93ae-6ba78d8be5cc",
    "account": "0x1111111111111111111111111111111111111111",
    "action": "GRANT",
    "occurredAt": "2026-06-01T12:00:00Z",
    "detailsHash": "0xabc123",
    "transactionHash": "0xaudit123"
  }
]
```

## Revoke Secret Access

`DELETE /api/v1/secrets/{id}/acl/{account}`

Returns `202 Accepted` with the submitted transaction hash.
After the ACL transaction is accepted by the blockchain adapter, the backend
publishes a server-generated `REVOKE` audit hash through
`SecretsAcl.auditEvent`.
If the audit publish attempt fails after the ACL transaction is submitted, this
endpoint still returns the ACL transaction hash so clients can correlate the
accepted operation without retrying it blindly.

```json
{
  "secretId": "21f51f8a-e0a4-457b-93ae-6ba78d8be5cc",
  "account": "0x1111111111111111111111111111111111111111",
  "transactionHash": "0xabc123"
}
```

## Blockchain ACL Configuration

The ACL adapter is disabled until both `blockchain.acl.contract-address` and
`blockchain.acl.private-key` are configured. Optional settings:

- `blockchain.acl.rpc-url`, default `http://localhost:8545`
- `blockchain.acl.gas-price`, default `20000000000`
- `blockchain.acl.gas-limit`, default `300000`
- `blockchain.acl.chain-id`, default `31337`

## Errors

Validation failures return `400 Bad Request`. Unknown secret ids return
`404 Not Found`. Duplicate names return `409 Conflict`. Blockchain adapter
failures return `502 Bad Gateway`.

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
