# Kubernetes manifests

This directory contains plain Kubernetes manifests for issue #17.

## Layout

- `base/` - deployable manifests for the current project topology

## What gets deployed

- `frontend` - static UI served by NGINX
- `secrets-api` - Spring Boot API
- `blockchain-node` - local Hardhat JSON-RPC node for the ACL contract
- `postgres` - persistent PostgreSQL instance
- `redis` - Redis cache instance
- `ingress` - routes `/` to the UI and `/api` to the backend

## Important topology note

`kms-service` is currently a shared backend module, not a standalone Spring Boot
service. Because of that, there is no separate Kubernetes `Deployment` for KMS.
The KMS functionality is packaged into the `secrets-api` container image.

## Build images

Build the images from the repository root:

```bash
docker build -f backend/secrets-api/Dockerfile -t ghcr.io/igorsatsyuk/blockchain-secrets-vault/secrets-api:0.1.0 backend
docker build -f frontend/Dockerfile -t ghcr.io/igorsatsyuk/blockchain-secrets-vault/frontend:0.1.0 frontend
docker build -f blockchain/Dockerfile -t ghcr.io/igorsatsyuk/blockchain-secrets-vault/blockchain-node:0.1.0 blockchain
```

Update tags in `base/*.yaml` if you publish different image names.

## Deploy

The base manifests intentionally fail closed for required secrets. At the
current stage of the project, only `secrets-api` is required for the working
MVP path; it still uses an in-memory repository and does not yet depend on the
deployed PostgreSQL or Redis pods. PostgreSQL and Redis are included here as
production-oriented infrastructure placeholders for the next persistence and
caching steps.

Provide Secrets API auth values before expecting the API pod to become healthy.
Without `POSTGRES_PASSWORD`, the PostgreSQL container will fail to start and
enter `CrashLoopBackOff`. Configure `postgres-secrets` only if you want the
PostgreSQL pod itself to run, which is optional for the current in-memory MVP.

1. Apply the base manifests.
2. Patch `secrets-api-secrets` with:
   - `SECRETS_AUTH_PASSWORD`
   - `SECRETS_AUTH_JWT_SECRET`
3. Optionally patch `postgres-secrets` with a real `POSTGRES_PASSWORD` if you
   want the PostgreSQL pod itself to start successfully.
4. Deploy the ACL contract and patch optional blockchain writer secrets:
   - `BLOCKCHAIN_ACL_CONTRACT_ADDRESS`
   - `BLOCKCHAIN_ACL_PRIVATE_KEY`
5. Restart the workloads after secret updates.

Apply the base manifests:

```bash
kubectl apply -k k8s/base
```

Patch the required Secrets API credentials:

```bash
kubectl -n blockchain-secrets-vault patch secret secrets-api-secrets \
  --type merge \
  -p '{"stringData":{"SECRETS_AUTH_PASSWORD":"replace-with-strong-password","SECRETS_AUTH_JWT_SECRET":"replace-with-at-least-32-bytes-of-secret-material"}}'
kubectl -n blockchain-secrets-vault rollout restart deployment/secrets-api
```

Optionally bring PostgreSQL up with a real password:

```bash
kubectl -n blockchain-secrets-vault patch secret postgres-secrets \
  --type merge \
  -p '{"stringData":{"POSTGRES_PASSWORD":"replace-with-strong-password"}}'

kubectl -n blockchain-secrets-vault rollout restart statefulset/postgres
```

## Enable blockchain ACL integration

The base deployment keeps blockchain ACL integration disabled until the Secrets
ACL contract address and writer private key are configured.

1. Deploy `SecretsAcl.sol` to the `blockchain-node` service.
2. Patch `secrets-api-secrets` with the contract address and writer key.
3. Restart `secrets-api`.

Example:

```bash
kubectl -n blockchain-secrets-vault patch secret secrets-api-secrets \
  --type merge \
  -p '{"stringData":{"BLOCKCHAIN_ACL_CONTRACT_ADDRESS":"0x...","BLOCKCHAIN_ACL_PRIVATE_KEY":"0x..."}}'

kubectl -n blockchain-secrets-vault rollout restart deployment/secrets-api
```

Once both values are non-empty, `secrets-api` automatically enables the
Web3j-backed ACL client.
