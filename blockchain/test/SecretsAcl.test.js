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
});
