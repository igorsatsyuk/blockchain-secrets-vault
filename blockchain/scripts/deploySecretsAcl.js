async function deploySecretsAcl(runtimeEnvironment, initialAdmin) {
  const [defaultAdmin] = await runtimeEnvironment.ethers.getSigners();
  const admin = initialAdmin || defaultAdmin.address;
  const contract = await runtimeEnvironment.ethers.deployContract("SecretsAcl", [admin]);
  await contract.waitForDeployment();

  const address = await contract.getAddress();
  console.log(`SecretsAcl deployed to: ${address}`);
  return address;
}

module.exports = {
  deploySecretsAcl
};
