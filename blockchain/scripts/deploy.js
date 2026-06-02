const hre = require("hardhat");
const { deploySecretsAcl } = require("./deploySecretsAcl");

async function main() {
  await deploySecretsAcl(hre);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
