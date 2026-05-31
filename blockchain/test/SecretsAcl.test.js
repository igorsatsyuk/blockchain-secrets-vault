const { expect } = require("chai");
const { ethers } = require("hardhat");
const { loadFixture } = require("@nomicfoundation/hardhat-toolbox/network-helpers");

describe("SecretsAcl - scaffolding and data model (#1)", function () {
  async function deployFixture() {
    const [deployer, admin, other] = await ethers.getSigners();
    const factory = await ethers.getContractFactory("SecretsAcl");
    const contract = await factory.deploy(admin.address);
    await contract.waitForDeployment();
    return { contract, factory, deployer, admin, other };
  }

  describe("deployment", function () {
    it("sets the initial admin", async function () {
      const { contract, admin } = await loadFixture(deployFixture);
      expect(await contract.admin()).to.equal(admin.address);
    });

    it("emits AdminTransferred from the zero address on construction", async function () {
      const { factory, admin } = await loadFixture(deployFixture);
      const contract = await factory.deploy(admin.address);
      await expect(contract.deploymentTransaction())
        .to.emit(contract, "AdminTransferred")
        .withArgs(ethers.ZeroAddress, admin.address);
    });

    it("reverts when the initial admin is the zero address", async function () {
      const { factory } = await loadFixture(deployFixture);
      await expect(
        factory.deploy(ethers.ZeroAddress)
      ).to.be.revertedWithCustomError(factory, "ZeroAddress");
    });

    it("starts with no registered secrets", async function () {
      const { contract } = await loadFixture(deployFixture);
      expect(await contract.totalSecrets()).to.equal(0n);
    });
  });

  describe("transferAdmin", function () {
    it("transfers admin and emits AdminTransferred", async function () {
      const { contract, admin, other } = await loadFixture(deployFixture);
      await expect(contract.connect(admin).transferAdmin(other.address))
        .to.emit(contract, "AdminTransferred")
        .withArgs(admin.address, other.address);
      expect(await contract.admin()).to.equal(other.address);
    });

    it("reverts when called by a non-admin account", async function () {
      const { contract, other } = await loadFixture(deployFixture);
      await expect(contract.connect(other).transferAdmin(other.address))
        .to.be.revertedWithCustomError(contract, "NotAuthorized")
        .withArgs(other.address);
    });

    it("reverts when the new admin is the zero address", async function () {
      const { contract, admin } = await loadFixture(deployFixture);
      await expect(
        contract.connect(admin).transferAdmin(ethers.ZeroAddress)
      ).to.be.revertedWithCustomError(contract, "ZeroAddress");
    });
  });

  describe("read-only accessors", function () {
    const unknownId = ethers.id("unknown-secret");

    it("reports that an unknown secret does not exist", async function () {
      const { contract } = await loadFixture(deployFixture);
      expect(await contract.secretExists(unknownId)).to.equal(false);
    });

    it("reverts getSecret for an unknown secret", async function () {
      const { contract } = await loadFixture(deployFixture);
      await expect(contract.getSecret(unknownId))
        .to.be.revertedWithCustomError(contract, "SecretNotFound")
        .withArgs(unknownId);
    });

    it("reverts secretIdAt when the index is out of bounds", async function () {
      const { contract } = await loadFixture(deployFixture);
      await expect(contract.secretIdAt(0))
        .to.be.revertedWithCustomError(contract, "IndexOutOfBounds")
        .withArgs(0, 0);
    });

    it("returns a zero-initialised access grant when none exists", async function () {
      const { contract, other } = await loadFixture(deployFixture);
      const grant = await contract.getAccess(unknownId, other.address);
      expect(grant.canRead).to.equal(false);
      expect(grant.canWrite).to.equal(false);
      expect(grant.exists).to.equal(false);
      expect(grant.updatedAt).to.equal(0n);
    });
  });

  describe("registerSecret (#2)", function () {
    const secretId = ethers.id("db/prod/password");
    const dataHash = ethers.id("ciphertext-hash");
    const uri = "s3://vault/db/prod/password";

    it("registers a secret owned by the caller", async function () {
      const { contract, other } = await loadFixture(deployFixture);
      await contract.connect(other).registerSecret(secretId, dataHash, uri);

      expect(await contract.secretExists(secretId)).to.equal(true);
      expect(await contract.totalSecrets()).to.equal(1n);
      expect(await contract.secretIdAt(0)).to.equal(secretId);

      const secret = await contract.getSecret(secretId);
      expect(secret.owner).to.equal(other.address);
      expect(secret.dataHash).to.equal(dataHash);
      expect(secret.uri).to.equal(uri);
      expect(secret.exists).to.equal(true);
      expect(secret.createdAt).to.be.greaterThan(0n);
      expect(secret.updatedAt).to.equal(secret.createdAt);
    });

    it("emits SecretRegistered with the owner and data hash", async function () {
      const { contract, other } = await loadFixture(deployFixture);
      const tx = await contract.connect(other).registerSecret(secretId, dataHash, uri);
      const block = await ethers.provider.getBlock(tx.blockNumber);

      await expect(tx)
        .to.emit(contract, "SecretRegistered")
        .withArgs(secretId, other.address, dataHash, block.timestamp);
    });

    it("tracks multiple registered secrets in the enumeration", async function () {
      const { contract, admin, other } = await loadFixture(deployFixture);
      const secondId = ethers.id("api/key");
      await contract.connect(other).registerSecret(secretId, dataHash, uri);
      await contract.connect(admin).registerSecret(secondId, dataHash, "s3://vault/api/key");

      expect(await contract.totalSecrets()).to.equal(2n);
      expect(await contract.secretIdAt(0)).to.equal(secretId);
      expect(await contract.secretIdAt(1)).to.equal(secondId);
    });

    it("reverts when the secret id is zero", async function () {
      const { contract, other } = await loadFixture(deployFixture);
      await expect(
        contract.connect(other).registerSecret(ethers.ZeroHash, dataHash, uri)
      ).to.be.revertedWithCustomError(contract, "InvalidSecretId");
    });

    it("reverts when the secret id is already registered", async function () {
      const { contract, other } = await loadFixture(deployFixture);
      await contract.connect(other).registerSecret(secretId, dataHash, uri);
      await expect(contract.connect(other).registerSecret(secretId, dataHash, uri))
        .to.be.revertedWithCustomError(contract, "SecretAlreadyExists")
        .withArgs(secretId);
    });
  });
});
