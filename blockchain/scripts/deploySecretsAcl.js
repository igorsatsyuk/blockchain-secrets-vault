async function deploySecretsAcl(runtimeEnvironment, initialAdmin) {
  let admin = initialAdmin;
  if (admin == null) {
    const [defaultAdmin] = await runtimeEnvironment.ethers.getSigners();
    admin = defaultAdmin.address;
  }

  const contract = await runtimeEnvironment.ethers.deployContract("SecretsAcl", [admin]);
  await contract.waitForDeployment();

  const address = await contract.getAddress();
  console.log(`SecretsAcl deployed to: ${address}`);
  return address;
}

module.exports = {
  deploySecretsAcl
};
