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

## Roadmap

See [ROADMAP.md](./ROADMAP.md) and the
[project board](https://github.com/users/igorsatsyuk/projects/4).

## License

MIT
