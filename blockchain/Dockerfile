FROM node:24-alpine

WORKDIR /app

RUN chown node:node /app

USER node

COPY --chown=node:node package.json package-lock.json ./
RUN npm ci

COPY --chown=node:node contracts contracts
COPY --chown=node:node scripts scripts
COPY --chown=node:node hardhat.config.js hardhat.config.js

EXPOSE 8545

CMD ["npx", "hardhat", "node", "--hostname", "0.0.0.0"]
