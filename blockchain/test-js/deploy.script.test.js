const { expect } = require("chai");
const { deploySecretsAcl } = require("../scripts/deploySecretsAcl");

describe("deploy script", function () {
  it("deploySecretsAcl deploys SecretsAcl with the requested admin and returns address", async function () {
    const deployedAddress = "0x1234567890123456789012345678901234567890";
    const adminAddress = "0x1111111111111111111111111111111111111111";
    let requestedContractName;
    let requestedConstructorArgs;
    let loggedMessage;

    const fakeRuntime = {
      ethers: {
        getSigners: async () => [{ address: adminAddress }],
        deployContract: async (contractName, constructorArgs) => {
          requestedContractName = contractName;
          requestedConstructorArgs = constructorArgs;
          return {
            waitForDeployment: async () => {},
            getAddress: async () => deployedAddress
          };
        }
      }
    };

    const originalLog = console.log;
    console.log = (message) => {
      loggedMessage = message;
    };

    try {
      const result = await deploySecretsAcl(fakeRuntime, adminAddress);

      expect(requestedContractName).to.equal("SecretsAcl");
      expect(requestedConstructorArgs).to.deep.equal([adminAddress]);
      expect(result).to.equal(deployedAddress);
      expect(loggedMessage).to.equal(`SecretsAcl deployed to: ${deployedAddress}`);
    } finally {
      console.log = originalLog;
    }
  });

  it("deploySecretsAcl uses the default signer address when admin is omitted", async function () {
    const deployedAddress = "0x2222222222222222222222222222222222222222";
    const defaultAdminAddress = "0x3333333333333333333333333333333333333333";
    let requestedConstructorArgs;

    const fakeRuntime = {
      ethers: {
        getSigners: async () => [{ address: defaultAdminAddress }],
        deployContract: async (_contractName, constructorArgs) => {
          requestedConstructorArgs = constructorArgs;
          return {
            waitForDeployment: async () => {},
            getAddress: async () => deployedAddress
          };
        }
      }
    };

    const originalLog = console.log;
    console.log = () => {};

    try {
      const result = await deploySecretsAcl(fakeRuntime);

      expect(requestedConstructorArgs).to.deep.equal([defaultAdminAddress]);
      expect(result).to.equal(deployedAddress);
    } finally {
      console.log = originalLog;
    }
  });
});
