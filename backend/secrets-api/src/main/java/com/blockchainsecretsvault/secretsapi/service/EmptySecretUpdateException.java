package com.blockchainsecretsvault.secretsapi.service;

public class EmptySecretUpdateException extends RuntimeException {

    public EmptySecretUpdateException() {
        super("At least one secret field must be provided for update");
    }
}
