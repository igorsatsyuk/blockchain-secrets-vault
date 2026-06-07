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

Apply the base manifests:

```bash
kubectl apply -k k8s/base
```

## Enable blockchain ACL integration

The base deployment keeps blockchain ACL integration disabled until the Secrets
ACL contract address and writer private key are configured.

1. Deploy the manifests.
2. Deploy `SecretsAcl.sol` to the `blockchain-node` service.
3. Patch `secrets-api-secrets` with:
   - `BLOCKCHAIN_ACL_CONTRACT_ADDRESS`
   - `BLOCKCHAIN_ACL_PRIVATE_KEY`
4. Restart `secrets-api`.

Example:

```bash
kubectl -n blockchain-secrets-vault patch secret secrets-api-secrets \
  --type merge \
  -p '{"stringData":{"BLOCKCHAIN_ACL_CONTRACT_ADDRESS":"0x...","BLOCKCHAIN_ACL_PRIVATE_KEY":"0x..."}}'

kubectl -n blockchain-secrets-vault rollout restart deployment/secrets-api
```

Once both values are non-empty, `secrets-api` automatically enables the
Web3j-backed ACL client.
