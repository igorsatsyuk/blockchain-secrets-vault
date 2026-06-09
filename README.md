# Blockchain Secrets Vault

Blockchain Secrets Vault is a zero‑trust distributed secrets storage system
that combines AES‑GCM envelope encryption, blockchain‑anchored ACL enforcement,
and Merkle‑verified audit events. Secrets are stored off‑chain in encrypted
form, while access rights and audit integrity are validated on‑chain via a
Solidity smart contract. The platform includes a reactive WebFlux API, a
dedicated KMS module with key rotation, an encrypted persistence layer, an
audit writer that publishes event hashes to the blockchain, and optional
Kubernetes/Grafana production deployment stack.

The project demonstrates:

- Zero-trust architecture
- Blockchain-based access control
- AES-GCM envelope encryption with DEK/KEK separation
- Key rotation
- On-chain access auditing
- Microservice architecture

## Architecture

![C4 context diagram](docs/diagrams/c4-context.png)

Full diagram: [docs/architecture.md](docs/architecture.md)

Secrets API contract: [docs/api/secrets-api.md](docs/api/secrets-api.md)

### Components

- **Secrets API** - CRUD over secrets and API contract for the MVP. KMS
  encryption/decryption and blockchain ACL checks are planned follow-up work
  (#8, #9).
- **KMS Service** - KEK generation, per-secret DEK envelope encryption, key rotation.
- **Blockchain ACL Contract** - stores ACL (who can read/write), revocations, and
  access audit.
- **Audit Writer** - writes hashes of access events to the blockchain.
- **Secrets UI** - secrets management, ACL management, audit viewing.

## Tech Stack

### Backend
- Java 25+, Spring Boot, Spring WebFlux, Spring Security
- Maven multi-module
- Web3j
- AES-GCM, RSA/ECDSA
- PostgreSQL / Redis

### Blockchain
- Hardhat, Solidity
- `SecretsAcl.sol` contract

### Frontend
- Dependency-free HTML, CSS, and JavaScript UI MVP
- Node.js built-in test runner with coverage gates

### Infrastructure
- Docker Compose, optional Kubernetes (manifests + Helm chart)

Kubernetes deployment docs: [k8s/README.md](k8s/README.md)

## Repository Layout

```text
blockchain-secrets-vault/
├─ backend/      # Spring Boot multi-module services
├─ blockchain/   # Hardhat project and Solidity contracts
├─ frontend/     # Secrets management UI
├─ docs/         # architecture and API documentation
└─ ROADMAP.md
```

## Frontend

Serve `frontend/` from the same origin as the backend to use the default
`/api/v1/secrets` endpoint. Opening `frontend/index.html` directly via `file://`
will not work with the default API base URL.

Run frontend tests and coverage checks:

```bash
cd frontend
npm test
```

## Docker Compose

### Overview

The `docker-compose.yml` file allows you to run the project in Docker, including:
- **PostgreSQL** - infrastructure service for future persistence work (port 5432)
- **Redis** - infrastructure service for future caching/integration work (port 6379)
- **Blockchain** - Hardhat node (port 8545)
- **Backend (Secrets API)** - Spring Boot application (port 8081)
- **Frontend** - Nginx web interface on `localhost:8080` with `/api/` reverse proxy to `secrets-api:8080` inside the Docker network

At the moment, the Secrets API still uses an in-memory repository, so secret
data does not persist across API restarts even though Postgres and Redis
containers are available in the stack.

### Requirements

- Docker (version 20.10+)
- Docker Compose (version 2.0+)

### Quick Start

#### 1. Start all services

Create a `.env` file in the repository root before the first start:

```bash
POSTGRES_PASSWORD=secretsvault-local-dev
SPRING_PROFILES_ACTIVE=dev
SECRETS_AUTH_PASSWORD=replace-with-a-strong-password
SECRETS_AUTH_JWT_SECRET=replace-with-a-32-byte-or-longer-secret
```

The auth variables are required by `secrets-api`, and the JWT secret must be at
least 32 bytes long.

Then start the stack:

```bash
docker compose up -d
```

The `-d` flag runs containers in the background.

#### 2. View logs

```bash
# All services
docker compose logs -f

# Specific service
docker compose logs -f secrets-api
docker compose logs -f blockchain
```

#### 3. Stop all services

```bash
docker compose down
```

#### 4. Delete all data (including database)

```bash
docker compose down -v
```

### Ports and Addresses

| Service | URL | Port |
|---------|-----|------|
| Frontend | http://localhost:8080 | 8080 |
| Backend API | http://localhost:8081 | 8081 |
| Blockchain (Hardhat) | http://localhost:8545 | 8545 |
| PostgreSQL | localhost:5432 | 5432 |
| Redis | localhost:6379 | 6379 |

Open `http://localhost:8080` for the UI. The frontend proxies `/api/*` calls to
`secrets-api` inside the Docker network, so the browser does not need a
separate backend base URL. Published ports are bound to `127.0.0.1`, so the
stack stays local to the development machine by default.

### Environment Variables

Variables are stored in the `.env` file:

```bash
POSTGRES_PASSWORD=secretsvault-local-dev
SPRING_PROFILES_ACTIVE=dev
SECRETS_AUTH_PASSWORD=replace-with-a-strong-password
SECRETS_AUTH_JWT_SECRET=replace-with-a-32-byte-or-longer-secret
```

Modify the `.env` file to configure the database password, Spring profile, and
Secrets API authentication values used by Docker Compose.

### Service Health Check

```bash
# Check status of all containers
docker compose ps

# View logs of specific services
docker compose logs postgres
docker compose logs redis
```

### Connecting to Services

#### PostgreSQL

```bash
docker compose exec postgres psql -U secretsvault -d secretsvault
```

#### Redis

```bash
docker compose exec redis redis-cli
```

### Development Commands

#### Rebuild images

```bash
# Rebuild all
docker compose build --no-cache

# Rebuild specific service
docker compose build --no-cache secrets-api
```

#### Restart a service

```bash
docker compose restart secrets-api
```

#### View logs in real-time

```bash
docker compose logs -f --tail=100 [service-name]
```

### Troubleshooting

#### Port already in use

If a port is already used by another application, modify the port mapping in `docker-compose.yml`:

```yaml
services:
  frontend:
    ports:
      - "127.0.0.1:8082:8080"  # Use 8082 instead of 8080
```

#### Containers fail to start

Check the logs:

```bash
docker compose logs [service-name]
```

#### Need to rebuild an image

```bash
docker compose down
docker compose build --no-cache
docker compose up -d
```

#### Clean everything and start over

```bash
docker compose down -v --remove-orphans
docker compose build --no-cache
docker compose up -d
```

### Network Details

All services are connected to the `blockchain-vault-network` bridge network, allowing them to communicate using hostnames:
- `postgres:5432`
- `redis:6379`
- `blockchain:8545`
- `secrets-api:8080`

### Additional Commands

#### View memory usage

```bash
docker stats
```

#### Clean up unused images and containers

```bash
docker compose down --remove-orphans
docker image prune -f
```

`docker image prune -f` removes dangling images across Docker on the machine.
Review the command before running it if you are working on multiple projects.

#### View environment variables

```bash
docker compose config
```

## Roadmap

See [ROADMAP.md](./ROADMAP.md) and the
[project board](https://github.com/users/igorsatsyuk/projects/4).

## License

MIT
