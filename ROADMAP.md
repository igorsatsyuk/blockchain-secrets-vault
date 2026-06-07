# Blockchain Secrets Vault - ROADMAP

## Phase 1 - Blockchain

- [x] [#1](https://github.com/igorsatsyuk/blockchain-secrets-vault/issues/1) `SecretsAcl.sol` smart contract scaffolding and data models
- [x] [#2](https://github.com/igorsatsyuk/blockchain-secrets-vault/issues/2) `registerSecret` implementation in ACL contract
- [x] [#3](https://github.com/igorsatsyuk/blockchain-secrets-vault/issues/3) `grantAccess` implementation for read/write permissions
- [x] [#4](https://github.com/igorsatsyuk/blockchain-secrets-vault/issues/4) `revokeAccess` implementation for permission revocation
- [x] [#5](https://github.com/igorsatsyuk/blockchain-secrets-vault/issues/5) `canRead` and `canWrite` access check functions
- [x] [#6](https://github.com/igorsatsyuk/blockchain-secrets-vault/issues/6) `auditEvent` function and contract audit events

## Phase 2 - Backend

- [x] [#7](https://github.com/igorsatsyuk/blockchain-secrets-vault/issues/7) Secrets API MVP with CRUD and API contracts
- [x] [#8](https://github.com/igorsatsyuk/blockchain-secrets-vault/issues/8) KMS Service with AES-GCM and key management
- [x] [#9](https://github.com/igorsatsyuk/blockchain-secrets-vault/issues/9) Blockchain adapter integration with ACL contract
- [x] [#10](https://github.com/igorsatsyuk/blockchain-secrets-vault/issues/10) Audit Writer for publishing access event hashes on-chain

## Phase 3 - Frontend

- [x] [#11](https://github.com/igorsatsyuk/blockchain-secrets-vault/issues/11) UI for secrets management
- [x] [#12](https://github.com/igorsatsyuk/blockchain-secrets-vault/issues/12) UI for ACL management
- [x] [#13](https://github.com/igorsatsyuk/blockchain-secrets-vault/issues/13) UI for audit history viewing

## Phase 4 - Security

- [x] [#14](https://github.com/igorsatsyuk/blockchain-secrets-vault/issues/14) Key rotation for secret encryption keys
- [x] [#15](https://github.com/igorsatsyuk/blockchain-secrets-vault/issues/15) Envelope encryption with DEK/KEK model
- [x] [#16](https://github.com/igorsatsyuk/blockchain-secrets-vault/issues/16) JWT authentication for UI and backend APIs

## Phase 5 - Production

- [x] [#17](https://github.com/igorsatsyuk/blockchain-secrets-vault/issues/17) Kubernetes manifests for all services
- [x] [#18](https://github.com/igorsatsyuk/blockchain-secrets-vault/issues/18) Helm chart for environment deployments
- [x] [#19](https://github.com/igorsatsyuk/blockchain-secrets-vault/issues/19) Grafana dashboards for observability and monitoring
