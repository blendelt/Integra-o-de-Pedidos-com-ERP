package com.example.orders.exception;

public class DuplicateExternalIdException extends RuntimeException {

    public DuplicateExternalIdException(String externalId) {
        super("Já existe um pedido com o externalId " + externalId);
    }
}
