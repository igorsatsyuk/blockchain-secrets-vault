package com.blockchainsecretsvault.secretsapi.service;

public class DuplicateSecretNameException extends RuntimeException {

    public DuplicateSecretNameException(String name) {
        super("Secret with name '" + name + "' already exists");
    }
}
