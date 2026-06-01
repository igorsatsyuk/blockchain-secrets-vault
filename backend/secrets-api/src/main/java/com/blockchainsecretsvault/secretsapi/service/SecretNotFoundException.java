package com.blockchainsecretsvault.secretsapi.service;

import java.util.UUID;

public class SecretNotFoundException extends RuntimeException {

    public SecretNotFoundException(UUID id) {
        super("Secret '" + id + "' was not found");
    }
}
