# Kubernetes manifests and Helm chart

This directory contains:

- `base/` - plain Kubernetes manifests from issue #17
- `helm/blockchain-secrets-vault/` - Helm chart for parameterized environment deployments (issue #18)
- `observability/grafana/dashboards/` - Grafana dashboard definitions for production monitoring (issue #19)

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

## Deploy with plain manifests (kustomize)

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

## Deploy with Helm (dev/stage/prod values)

Install or upgrade:

```bash
# create local files with secret values (do not commit them)
printf 'replace-with-strong-password' > auth-password.txt
printf 'replace-with-at-least-32-bytes-of-secret-material' > jwt-secret.txt
printf 'replace-with-strong-password' > postgres-password.txt

helm upgrade --install blockchain-secrets-vault k8s/helm/blockchain-secrets-vault \
  --namespace blockchain-secrets-vault \
  --create-namespace \
  -f k8s/helm/blockchain-secrets-vault/values-dev.yaml \
  --set-file secretsApi.secrets.authPassword=auth-password.txt \
  --set-file secretsApi.secrets.authJwtSecret=jwt-secret.txt \
  --set-file postgres.auth.password=postgres-password.txt
```

Note: Helm still stores these values in-cluster as rendered Kubernetes Secrets
and inside Helm release metadata/history. Restrict access to the namespace and
to Helm release secrets accordingly.

Use a different environment values file:

- `values-dev.yaml`
- `values-stage.yaml`
- `values-prod.yaml`

All Helm environments require overriding `secretsApi.secrets.authPassword`,
`secretsApi.secrets.authJwtSecret`, and `postgres.auth.password`.
Unlike `k8s/base`, this chart always deploys PostgreSQL, so a non-empty
`postgres.auth.password` is mandatory for successful startup.

For production-like environments, use `values-prod.yaml` and override secrets:

```bash
# create local files with production secret values (do not commit them)
printf 'replace-with-strong-password' > auth-password.txt
printf 'replace-with-at-least-32-bytes-of-secret-material' > jwt-secret.txt
printf 'replace-with-strong-password' > postgres-password.txt

helm upgrade --install blockchain-secrets-vault k8s/helm/blockchain-secrets-vault \
  --namespace blockchain-secrets-vault \
  --create-namespace \
  -f k8s/helm/blockchain-secrets-vault/values-prod.yaml \
  --set-file secretsApi.secrets.authPassword=auth-password.txt \
  --set-file secretsApi.secrets.authJwtSecret=jwt-secret.txt \
  --set-file postgres.auth.password=postgres-password.txt
```

## Grafana dashboards

Dashboards for health, errors/latency, and resource performance are provided in:

- `k8s/observability/grafana/dashboards/service-health-overview.json`
- `k8s/observability/grafana/dashboards/api-errors-and-latency.json`
- `k8s/observability/grafana/dashboards/resource-performance.json`

Import them into Grafana and connect to a Prometheus datasource that scrapes:

- Kubernetes metrics (`kube-state-metrics`, `cAdvisor`/`kubelet`)
- ingress-nginx metrics (`nginx_ingress_controller_*`) for HTTP error/latency panels
