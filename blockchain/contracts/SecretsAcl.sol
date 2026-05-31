// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

/// @title SecretsAcl
/// @notice On-chain access control list and audit registry for the
///         Blockchain Secrets Vault. Secrets themselves are stored off-chain in
///         encrypted form; this contract holds only metadata, access grants and
///         immutable audit records.
/// @dev Issue #1 establishes the data model, storage layout, events, custom
///      errors and read-only accessors. Mutating operations (registerSecret,
///      grantAccess, revokeAccess, canRead/canWrite, auditEvent) are implemented
///      in follow-up issues #2-#6.
contract SecretsAcl {
    // ---------------------------------------------------------------------
    // Data model
    // ---------------------------------------------------------------------

    /// @notice Type of operation captured in an on-chain audit record.
    enum AuditAction {
        REGISTER,
        GRANT,
        REVOKE,
        READ,
        WRITE
    }

    /// @notice Metadata describing an off-chain secret.
    /// @dev `dataHash` binds the on-chain record to the off-chain ciphertext.
    ///      `uri` references the off-chain storage location.
    struct Secret {
        address owner;
        bytes32 dataHash;
        string uri;
        uint256 createdAt;
        uint256 updatedAt;
        bool exists;
    }

    /// @notice Access grant for a single (secret, account) pair.
    struct AccessGrant {
        bool canRead;
        bool canWrite;
        bool exists;
        uint256 updatedAt;
    }

    // ---------------------------------------------------------------------
    // Storage
    // ---------------------------------------------------------------------

    /// @notice Administrative account allowed to perform privileged operations.
    address public admin;

    /// @dev Enumerable list of registered secret identifiers.
    bytes32[] internal _secretIds;

    /// @dev secretId => secret metadata.
    mapping(bytes32 => Secret) internal _secrets;

    /// @dev secretId => account => access grant.
    mapping(bytes32 => mapping(address => AccessGrant)) internal _acl;

    // ---------------------------------------------------------------------
    // Events
    // ---------------------------------------------------------------------

    event AdminTransferred(address indexed previousAdmin, address indexed newAdmin);

    event SecretRegistered(
        bytes32 indexed secretId,
        address indexed owner,
        bytes32 dataHash,
        uint256 timestamp
    );

    event AccessGranted(
        bytes32 indexed secretId,
        address indexed account,
        bool canRead,
        bool canWrite,
        uint256 timestamp
    );

    event AccessRevoked(
        bytes32 indexed secretId,
        address indexed account,
        uint256 timestamp
    );

    event AccessAudited(
        bytes32 indexed secretId,
        address indexed account,
        AuditAction action,
        bytes32 detailsHash,
        uint256 timestamp
    );

    // ---------------------------------------------------------------------
    // Custom errors
    // ---------------------------------------------------------------------

    error ZeroAddress();
    error InvalidSecretId();
    error SecretNotFound(bytes32 secretId);
    error SecretAlreadyExists(bytes32 secretId);
    error NotAuthorized(address account);
    error IndexOutOfBounds(uint256 index, uint256 length);

    // ---------------------------------------------------------------------
    // Modifiers
    // ---------------------------------------------------------------------

    modifier onlyAdmin() {
        if (msg.sender != admin) {
            revert NotAuthorized(msg.sender);
        }
        _;
    }

    // ---------------------------------------------------------------------
    // Construction
    // ---------------------------------------------------------------------

    /// @param initialAdmin Account granted administrative privileges.
    constructor(address initialAdmin) {
        if (initialAdmin == address(0)) {
            revert ZeroAddress();
        }
        admin = initialAdmin;
        emit AdminTransferred(address(0), initialAdmin);
    }

    // ---------------------------------------------------------------------
    // Administration
    // ---------------------------------------------------------------------

    /// @notice Transfers administrative privileges to a new account.
    /// @param newAdmin The account that becomes the new admin.
    function transferAdmin(address newAdmin) external onlyAdmin {
        if (newAdmin == address(0)) {
            revert ZeroAddress();
        }
        address previous = admin;
        admin = newAdmin;
        emit AdminTransferred(previous, newAdmin);
    }

    // ---------------------------------------------------------------------
    // Secret registration
    // ---------------------------------------------------------------------

    /// @notice Registers a new secret owned by the caller.
    /// @dev The caller becomes the secret owner and may later manage its ACL.
    ///      Reverts with {InvalidSecretId} for a zero identifier and with
    ///      {SecretAlreadyExists} when the identifier is already in use.
    /// @param secretId Unique identifier of the secret (e.g. keccak256 of its name).
    /// @param dataHash Hash binding the record to the off-chain ciphertext.
    /// @param uri      Reference to the off-chain storage location.
    function registerSecret(
        bytes32 secretId,
        bytes32 dataHash,
        string calldata uri
    ) external {
        if (secretId == bytes32(0)) {
            revert InvalidSecretId();
        }
        if (_secrets[secretId].exists) {
            revert SecretAlreadyExists(secretId);
        }

        Secret storage secret = _secrets[secretId];
        secret.owner = msg.sender;
        secret.dataHash = dataHash;
        secret.uri = uri;
        secret.createdAt = block.timestamp;
        secret.updatedAt = block.timestamp;
        secret.exists = true;

        _secretIds.push(secretId);

        emit SecretRegistered(secretId, msg.sender, dataHash, block.timestamp);
    }

    // ---------------------------------------------------------------------
    // Read-only accessors
    // ---------------------------------------------------------------------

    /// @notice Number of registered secrets.
    function totalSecrets() external view returns (uint256) {
        return _secretIds.length;
    }

    /// @notice Returns the secret identifier stored at `index`.
    function secretIdAt(uint256 index) external view returns (bytes32) {
        uint256 length = _secretIds.length;
        if (index >= length) {
            revert IndexOutOfBounds(index, length);
        }
        return _secretIds[index];
    }

    /// @notice Whether a secret with `secretId` has been registered.
    function secretExists(bytes32 secretId) public view returns (bool) {
        return _secrets[secretId].exists;
    }

    /// @notice Returns the metadata of a registered secret.
    /// @dev Reverts with {SecretNotFound} when the secret is unknown.
    function getSecret(bytes32 secretId) external view returns (Secret memory) {
        Secret memory secret = _secrets[secretId];
        if (!secret.exists) {
            revert SecretNotFound(secretId);
        }
        return secret;
    }

    /// @notice Returns the access grant for an (secret, account) pair.
    /// @dev Returns a zero-initialised grant when none has been issued.
    function getAccess(bytes32 secretId, address account)
        external
        view
        returns (AccessGrant memory)
    {
        return _acl[secretId][account];
    }
}
